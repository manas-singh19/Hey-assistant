package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object DeviceController {

    fun executeAction(context: Context, action: String, parameter: String) {
        try {
            when (action) {
                "PLAY_YOUTUBE" -> {
                    playYouTube(context, parameter)
                }
                "OPEN_YOUTUBE" -> {
                    openYouTube(context)
                }
                "OPEN_GALLERY" -> {
                    openGallery(context)
                }
                "OPEN_CAMERA" -> {
                    openCamera(context)
                }
                "WEB_SEARCH" -> {
                    webSearch(context, parameter)
                }
                "SET_ALARM" -> {
                    setAlarm(context, parameter)
                }
                "SET_TIMER" -> {
                    setTimer(context, parameter)
                }
                "NAVIGATE_TO" -> {
                    navigateTo(context, parameter)
                }
                "DIAL_PHONE" -> {
                    dialPhone(context, parameter)
                }
                "SEND_EMAIL" -> {
                    sendEmail(context, parameter)
                }
                "OPEN_APP" -> {
                    openOptionalApp(context, parameter)
                }
                else -> {
                    Toast.makeText(context, "Conversational response returned.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch app: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun playYouTube(context: Context, query: String) {
        val searchQuery = query.ifBlank { "popular music" }
        // Use standard web intent to open youtube which natively routes to the YouTube app cleanly
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(searchQuery)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(webIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open YouTube.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openYouTube(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
        if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launchIntent)
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        }
    }

    private fun openGallery(context: Context) {
        // Try viewing standard gallery images
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to Category App Gallery
            try {
                val altIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(altIntent)
            } catch (e2: Exception) {
                // Final fallback directly to system Photos/MediaStore provider
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                } catch (e3: Exception) {
                    Toast.makeText(context, "No gallery app discovered.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openCamera(context: Context) {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: Open system's default camera app viewfinder
            try {
                val altIntent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(altIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "No camera application discovered.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun webSearch(context: Context, query: String) {
        val searchQuery = query.ifBlank { "AI Assistant" }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(searchQuery)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun setAlarm(context: Context, timeString: String) {
        // Simple heuristic: extract digits. Ideally handled by LLM.
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
            putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Alarm set by Assistant")
        }
        context.startActivity(intent)
    }

    private fun setTimer(context: Context, secondsString: String) {
        val seconds = secondsString.toIntOrNull() ?: 60 // default 60s
        val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, false)
        }
        context.startActivity(intent)
    }

    private fun navigateTo(context: Context, destination: String) {
        val q = destination.ifBlank { "gas station" }
        val uri = Uri.parse("google.navigation:q=${Uri.encode(q)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(q)}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        }
    }

    private fun dialPhone(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun sendEmail(context: Context, subject: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun openOptionalApp(context: Context, appName: String) {
        if (appName.isBlank()) return

        val pm = context.packageManager
        val packages = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        var launchIntent: Intent? = null
        
        // Exact match
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString()
            if (label.equals(appName, ignoreCase = true)) {
                launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    break
                }
            }
        }
        
        // Partial match
        if (launchIntent == null) {
            for (app in packages) {
                val label = pm.getApplicationLabel(app).toString()
                if (label.contains(appName, ignoreCase = true)) {
                    launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        break
                    }
                }
            }
        }
        
        if (launchIntent != null) {
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(launchIntent)
        } else {
            // Open Play Store
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(appName)}&c=apps")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(playStoreIntent)
            } catch (e: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(appName)}&c=apps")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
            }
        }
    }
}
