// Quick test - run this with: kotlinc -script test_player.kts
// Or we can just add debug output to the app

import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader

// Test if we can reach YouTube Music API
fun main() {
    println("Testing YouTube Music API...")
    
    // Test basic connectivity
    try {
        val url = URL("https://music.youtube.com")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val code = conn.responseCode
        println("YouTube Music reachable: HTTP $code")
        conn.disconnect()
    } catch (e: Exception) {
        println("Cannot reach YouTube Music: ${e.message}")
    }
    
    println("\nCheck the player_debug.log file for details after clicking a song in the app.")
}
