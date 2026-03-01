package com.example.ricosshisen_sho

import android.content.Context
import android.content.SharedPreferences
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import kotlinx.coroutines.*

data class Tile(
    val type: Int,
    val isSelected: Boolean = false,
    val isRemoved: Boolean = false,
    val imageName: String = "",
    val isHint: Boolean = false
)

enum class GameState {
    PLAYING, PAUSED, OPTIONS, SCORE, WON, NO_MOVES, ABOUT
}

class ShisenShoGame(initialRows: Int, initialCols: Int, val context: Context) {
    private val soundPool: SoundPool = SoundPool.Builder().setMaxStreams(5).build()
    private val prefs: SharedPreferences = context.getSharedPreferences("ShisenShoPrefs", Context.MODE_PRIVATE)

    var rows by mutableStateOf(prefs.getInt("grid_rows", initialRows))
    var cols by mutableStateOf(prefs.getInt("grid_cols", initialCols))
    var boardMode by mutableStateOf(prefs.getString("board_mode", "standard") ?: "standard")
    var boardWidthScale by mutableStateOf(1f)

    var board by mutableStateOf(List(rows) { List(cols) { Tile(0) } })
    var selectedTile by mutableStateOf<Pair<Int, Int>?>(null)
    var gameState by mutableStateOf(GameState.PLAYING)
    var timeSeconds by mutableLongStateOf(0L)

    // HINT LOGIC
    val hintCooldownSeconds = 30L
    var lastHintTime by mutableLongStateOf(-hintCooldownSeconds)
    val isHintAvailable: Boolean get() = timeSeconds >= lastHintTime + hintCooldownSeconds
    val hintSecondsRemaining: Long get() = ((lastHintTime + hintCooldownSeconds) - timeSeconds).coerceAtLeast(0L)

    // SHUFFLE LOGIC (NEW CONSTRAINTS)
    val shuffleCooldownSeconds = 15L
    var lastShuffleTime by mutableLongStateOf(-shuffleCooldownSeconds)
    var shufflesRemaining by mutableIntStateOf(5)
    val isShuffleAvailable: Boolean get() = shufflesRemaining > 0 && timeSeconds >= lastShuffleTime + shuffleCooldownSeconds
    val shuffleSecondsRemaining: Long get() = ((lastShuffleTime + shuffleCooldownSeconds) - timeSeconds).coerceAtLeast(0L)

    var lastPath by mutableStateOf<List<Pair<Int, Int>>?>(null)
    private val gameScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var isSoundEnabled by mutableStateOf(prefs.getBoolean("sound_enabled", true))
        private set

    fun toggleSound(enabled: Boolean) {
        isSoundEnabled = enabled
        prefs.edit { putBoolean("sound_enabled", enabled) }
    }

    var bgThemeColor by mutableStateOf(Color(0xFF002147))

    private val tileTypes = listOf(
        "tile_dot_1", "tile_dot_2", "tile_dot_3", "tile_dot_4", "tile_dot_5", "tile_dot_6", "tile_dot_7", "tile_dot_8", "tile_dot_9",
        "tile_bamboo_1", "tile_bamboo_2", "tile_bamboo_3", "tile_bamboo_4", "tile_bamboo_5", "tile_bamboo_6", "tile_bamboo_7", "tile_bamboo_8", "tile_bamboo_9",
        "tile_char_1", "tile_char_2", "tile_char_3", "tile_char_4", "tile_char_5", "tile_char_6", "tile_char_7", "tile_char_8", "tile_char_9",
        "tile_wind_e", "tile_wind_s", "tile_wind_w", "tile_wind_n",
        "tile_drag_r", "tile_drag_g", "tile_drag_b"
    )

    init {
        initializeBoard()
    }

    fun initializeBoard() {
        val totalTiles = rows * cols
        val tilesList = mutableListOf<Tile>()
        var typeIndex = 0

        while (tilesList.size < totalTiles) {
            val name = tileTypes[typeIndex % tileTypes.size]
            repeat(4) {
                if (tilesList.size < totalTiles) {
                    tilesList.add(Tile(type = typeIndex % tileTypes.size, imageName = name))
                }
            }
            typeIndex++
        }

        tilesList.shuffle()
        board = List(rows) { r -> List(cols) { c -> tilesList[r * cols + c] } }
        selectedTile = null
        timeSeconds = 0
        shufflesRemaining = 5
        lastShuffleTime = -shuffleCooldownSeconds
        gameState = GameState.PLAYING

        if (findAnyMatch() == null) silentShuffle()
    }

    private fun silentShuffle() {
        val activeTiles = board.flatten().filter { !it.isRemoved }
        if (activeTiles.isEmpty()) return
        val shuffledImages = activeTiles.map { it.imageName }.shuffled()
        var imageIdx = 0
        board = board.map { row ->
            row.map { tile ->
                if (!tile.isRemoved) tile.copy(imageName = shuffledImages[imageIdx++], isSelected = false, isHint = false) else tile
            }
        }
        if (findAnyMatch() == null) silentShuffle()
    }

    fun shuffleBoard() {
        if (!isShuffleAvailable) return
        val activeTiles = board.flatten().filter { !it.isRemoved }
        if (activeTiles.isEmpty()) return
        val shuffledImages = activeTiles.map { it.imageName }.shuffled()
        var imageIdx = 0
        board = board.map { row ->
            row.map { tile ->
                if (!tile.isRemoved) tile.copy(imageName = shuffledImages[imageIdx++], isSelected = false, isHint = false) else tile
            }
        }

        shufflesRemaining--
        lastShuffleTime = timeSeconds
        selectedTile = null
        if (findAnyMatch() == null) silentShuffle()
        gameState = GameState.PLAYING
    }

    // YOUR ORIGINAL PATHFINDING LOGIC
    fun findPath(p1: Pair<Int, Int>, p2: Pair<Int, Int>): List<Pair<Int, Int>>? {
        val r1 = p1.first; val c1 = p1.second
        val r2 = p2.first; val c2 = p2.second

        // 0 turns (Straight line)
        checkStraight(r1, c1, r2, c2)?.let { return it }

        // 1 turn (L-shape)
        checkStraight(r1, c1, r1, c2)?.let { path1 ->
            if (isRemoved(r1, c2)) {
                checkStraight(r1, c2, r2, c2)?.let { path2 -> return path1 + path2.drop(1) }
            }
        }
        checkStraight(r1, c1, r2, c1)?.let { path1 ->
            if (isRemoved(r2, c1)) {
                checkStraight(r2, c1, r2, c2)?.let { path2 -> return path1 + path2.drop(1) }
            }
        }

        // 2 turns (U-shape or Z-shape)
        // Check horizontal scans
        for (c in -1..cols) {
            if (c == c1 || c == c2) continue
            if (isRemovedOrOutside(r1, c) && isRemovedOrOutside(r2, c)) {
                val pathA = checkStraight(r1, c1, r1, c)
                val pathB = checkStraight(r1, c, r2, c)
                val pathC = checkStraight(r2, c, r2, c2)
                if (pathA != null && pathB != null && pathC != null) return pathA + pathB.drop(1) + pathC.drop(1)
            }
        }
        // Check vertical scans
        for (r in -1..rows) {
            if (r == r1 || r == r2) continue
            if (isRemovedOrOutside(r, c1) && isRemovedOrOutside(r, c2)) {
                val pathA = checkStraight(r1, c1, r, c1)
                val pathB = checkStraight(r, c1, r, c2)
                val pathC = checkStraight(r, c2, r2, c2)
                if (pathA != null && pathB != null && pathC != null) return pathA + pathB.drop(1) + pathC.drop(1)
            }
        }
        return null
    }

    private fun checkStraight(r1: Int, c1: Int, r2: Int, c2: Int): List<Pair<Int, Int>>? {
        if (r1 != r2 && c1 != c2) return null
        val path = mutableListOf<Pair<Int, Int>>()
        if (r1 == r2) {
            val minC = minOf(c1, c2); val maxC = maxOf(c1, c2)
            for (c in minC..maxC) {
                if (c != c1 && c != c2 && !isRemovedOrOutside(r1, c)) return null
                path.add(r1 to c)
            }
        } else {
            val minR = minOf(r1, r2); val maxR = maxOf(r1, r2)
            for (r in minR..maxR) {
                if (r != r1 && r != r2 && !isRemovedOrOutside(r, c1)) return null
                path.add(r to c1)
            }
        }
        return if (c1 > c2 || r1 > r2) path.reversed() else path
    }

    private fun isRemoved(r: Int, c: Int): Boolean = r in 0 until rows && c in 0 until cols && board[r][c].isRemoved
    private fun isRemovedOrOutside(r: Int, c: Int): Boolean = r !in 0 until rows || c !in 0 until cols || board[r][c].isRemoved

    private fun findAnyMatch(): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        val active = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows) for (c in 0 until cols) if (!board[r][c].isRemoved) active.add(r to c)
        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                val p1 = active[i]; val p2 = active[j]
                if (board[p1.first][p1.second].imageName == board[p2.first][p2.second].imageName) {
                    if (findPath(p1, p2) != null) return p1 to p2
                }
            }
        }
        return null
    }

    fun showHint() {
        if (!isHintAvailable) return
        val match = findAnyMatch() ?: return
        val (p1, p2) = match
        board = board.mapIndexed { ri, row ->
            row.mapIndexed { ci, tile ->
                if ((ri == p1.first && ci == p1.second) || (ri == p2.first && ci == p2.second)) {
                    tile.copy(isHint = true)
                } else tile.copy(isHint = false)
            }
        }
        lastHintTime = timeSeconds
        gameScope.launch {
            delay(2000)
            board = board.map { row -> row.map { it.copy(isHint = false) } }
        }
    }

    fun onTileClick(r: Int, c: Int, view: android.view.View) {
        if (gameState != GameState.PLAYING || board[r][c].isRemoved) return
        val prev = selectedTile
        if (prev == null) {
            updateTile(r, c, board[r][c].copy(isSelected = true))
            selectedTile = r to c
        } else {
            val (pr, pc) = prev
            if (pr == r && pc == c) {
                updateTile(r, c, board[r][c].copy(isSelected = false))
                selectedTile = null
            } else if (board[pr][pc].imageName == board[r][c].imageName) {
                val path = findPath(pr to pc, r to c)
                if (path != null) {
                    lastPath = path
                    updateTile(pr, pc, board[pr][pc].copy(isRemoved = true, isSelected = false))
                    updateTile(r, c, board[r][c].copy(isRemoved = true, isSelected = false))
                    selectedTile = null
                    checkGameState()
                } else {
                    updateTile(pr, pc, board[pr][pc].copy(isSelected = false))
                    updateTile(r, c, board[r][c].copy(isSelected = true))
                    selectedTile = r to c
                }
            } else {
                updateTile(pr, pc, board[pr][pc].copy(isSelected = false))
                updateTile(r, c, board[r][c].copy(isSelected = true))
                selectedTile = r to c
            }
        }
    }

    private fun updateTile(r: Int, c: Int, newTile: Tile) {
        board = board.mapIndexed { ri, row ->
            if (ri == r) row.mapIndexed { ci, tile -> if (ci == c) newTile else tile } else row
        }
    }

    private fun checkGameState() {
        if (board.flatten().all { it.isRemoved }) gameState = GameState.WON
        else if (findAnyMatch() == null) gameState = GameState.NO_MOVES
    }

    fun formatTime(): String = "%02d:%02d".format(timeSeconds / 60, timeSeconds % 60)
    fun updateGridSize(r: Int, c: Int, m: String) { rows = r; cols = c; boardMode = m; initializeBoard() }
    fun getDifficultyLabel(r: Int = rows, c: Int = cols, m: String = boardMode): String = if (r <= 5) "Easy" else if (r <= 7) "Normal" else "Hard"
    fun releaseSounds() { gameScope.cancel() }
    fun saveScore(n: String, t: Long) {}
    fun getTopScores(r: Int, c: Int, m: String): List<Pair<String, String>> = emptyList()
    fun clearScores(r: Int, c: Int, m: String) {}
}