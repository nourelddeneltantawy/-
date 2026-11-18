package com.example.data.repository

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import com.example.data.model.McuPinMapping
import com.example.data.model.McuProcessor
import com.example.data.model.McuProtocolDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

data class LoadedMcuDatabaseResult(
    val processors: List<McuProcessor>,
    val loadedFileNames: List<String>,
    val errors: List<String> = emptyList()
)

object McuDatabaseRepository {
    private const val TAG = "McuDatabaseRepository"
    private const val DATABASES_ASSET_DIR = "databases"

    @Volatile
    private var cachedResult: LoadedMcuDatabaseResult? = null

    suspend fun loadAllDatabases(
        context: Context,
        forceReload: Boolean = false
    ): LoadedMcuDatabaseResult = withContext(Dispatchers.IO) {
        if (!forceReload) {
            cachedResult?.let { return@withContext it }
        }

        val assetManager = context.applicationContext.assets
        val files = try {
            assetManager.list(DATABASES_ASSET_DIR)
                ?.filter { it.endsWith(".json", ignoreCase = true) }
                ?.sorted()
                ?: emptyList()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to list assets/$DATABASES_ASSET_DIR", e)
            emptyList()
        }

        val allProcessors = ArrayList<McuProcessor>(2400)
        val loadedFiles = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for (fileName in files) {
            try {
                assetManager.open("$DATABASES_ASSET_DIR/$fileName").use { stream ->
                    JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        val parsed = parseDatabaseJson(reader, fileName)
                        allProcessors.addAll(parsed)
                        loadedFiles.add(fileName)
                        Log.i(TAG, "Loaded ${parsed.size} processors from $fileName")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error parsing $fileName", e)
                errors.add("$fileName: ${e.message ?: "خطأ في قراءة الملف"}")
            }
        }

        val result = LoadedMcuDatabaseResult(
            processors = allProcessors,
            loadedFileNames = loadedFiles,
            errors = errors
        )
        cachedResult = result
        result
    }

    private fun parseDatabaseJson(reader: JsonReader, fileName: String): List<McuProcessor> {
        val list = ArrayList<McuProcessor>(2200)
        var rootManufacturer = fileName.substringBefore("_").replaceFirstChar { it.uppercase() }

        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "manufacturer" -> {
                            if (reader.peek() == JsonToken.STRING) {
                                rootManufacturer = reader.nextString()
                            } else {
                                reader.skipValue()
                            }
                        }
                        "processors" -> {
                            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    readProcessorObject(reader, rootManufacturer, fileName)?.let {
                                        list.add(it)
                                    }
                                }
                                reader.endArray()
                            } else {
                                reader.skipValue()
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) {
                    readProcessorObject(reader, rootManufacturer, fileName)?.let {
                        list.add(it)
                    }
                }
                reader.endArray()
            }
            else -> reader.skipValue()
        }
        return list
    }

    private fun readProcessorObject(
        reader: JsonReader,
        fallbackManufacturer: String,
        sourceFile: String
    ): McuProcessor? {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }

        var id = ""
        var manufacturer = fallbackManufacturer
        var mainFamily = ""
        var subFamily = ""
        var partNumber = ""
        var pins = 0
        var flashKb = 0
        var displayLabel = ""
        var voltage: String? = null
        var protocol: String? = null
        var customPinout: List<McuPinMapping>? = null
        var programStartOverride: Int? = null
        var programEndOverride: Int? = null
        var dataFlashStartOverride: Int? = null
        var dataFlashEndOverride: Int? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextStringOrEmpty()
                "manufacturer" -> manufacturer = reader.nextStringOrEmpty().ifBlank { fallbackManufacturer }
                "main_family" -> mainFamily = reader.nextStringOrEmpty()
                "sub_family" -> subFamily = reader.nextStringOrEmpty()
                "part_number" -> partNumber = reader.nextStringOrEmpty()
                "pins" -> pins = reader.nextIntSafe()
                "flash_kb" -> flashKb = reader.nextIntSafe()
                "display_label" -> displayLabel = reader.nextStringOrEmpty()
                "voltage" -> voltage = reader.nextStringOrEmpty().ifBlank { null }
                "protocol" -> protocol = reader.nextStringOrEmpty().ifBlank { null }
                "program_start", "programStartAddress" -> programStartOverride = reader.nextHexOrIntSafe()
                "program_end", "programEndAddress" -> programEndOverride = reader.nextHexOrIntSafe()
                "data_flash_start", "dataFlashStartAddress" -> dataFlashStartOverride = reader.nextHexOrIntSafe()
                "data_flash_end", "dataFlashEndAddress" -> dataFlashEndOverride = reader.nextHexOrIntSafe()
                "pinout" -> customPinout = readPinoutArray(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (partNumber.isBlank()) return null

        val resolvedSub = subFamily.ifBlank { mainFamily }
        val resolvedLabel = displayLabel.ifBlank {
            if (pins > 0 && flashKb > 0) "$partNumber (${pins}-Pin - ${flashKb}KB)" else partNumber
        }
        val resolvedId = id.ifBlank { "${manufacturer}_${mainFamily}_${partNumber}_$pins" }

        return McuProcessor(
            id = resolvedId,
            manufacturer = manufacturer,
            mainFamily = mainFamily.ifBlank { "General" },
            subFamily = resolvedSub.ifBlank { "Standard" },
            partNumber = partNumber,
            pins = pins,
            flashKb = flashKb,
            displayLabel = resolvedLabel,
            recommendedVoltage = voltage ?: McuProtocolDefaults.defaultVoltage(mainFamily),
            protocolName = protocol ?: McuProtocolDefaults.defaultProtocolName(mainFamily, resolvedSub),
            protocolType = McuProtocolDefaults.resolveProtocolType(mainFamily),
            sourceFile = sourceFile,
            customPinout = customPinout,
            programStartOverride = programStartOverride,
            programEndOverride = programEndOverride,
            dataFlashStartOverride = dataFlashStartOverride,
            dataFlashEndOverride = dataFlashEndOverride
        )
    }

    private fun readPinoutArray(reader: JsonReader): List<McuPinMapping>? {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue()
            return null
        }
        val mappings = mutableListOf<McuPinMapping>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                var progPin = ""
                var mcuName = ""
                var mcuNumber = ""
                var direction = "ثنائي الاتجاه"
                var notes = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "programmer_pin" -> progPin = reader.nextStringOrEmpty()
                        "mcu_pin_name" -> mcuName = reader.nextStringOrEmpty()
                        "mcu_pin_number" -> mcuNumber = reader.nextStringOrEmpty()
                        "direction" -> direction = reader.nextStringOrEmpty().ifBlank { direction }
                        "notes" -> notes = reader.nextStringOrEmpty()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (progPin.isNotBlank() || mcuName.isNotBlank()) {
                    mappings.add(
                        McuPinMapping(
                            programmerPin = progPin,
                            mcuPinName = mcuName,
                            mcuPinNumber = mcuNumber,
                            signalDirection = direction,
                            notes = notes
                        )
                    )
                }
            } else {
                reader.skipValue()
            }
        }
        reader.endArray()
        return mappings.takeIf { it.isNotEmpty() }
    }

    private fun JsonReader.nextStringOrEmpty(): String {
        return when (peek()) {
            JsonToken.STRING -> nextString()
            JsonToken.NUMBER -> nextString()
            JsonToken.NULL -> {
                nextNull()
                ""
            }
            else -> {
                skipValue()
                ""
            }
        }
    }

    private fun JsonReader.nextIntSafe(): Int {
        return when (peek()) {
            JsonToken.NUMBER -> try {
                nextInt()
            } catch (_: Throwable) {
                nextDouble().toInt()
            }
            JsonToken.STRING -> nextString().toIntOrNull() ?: 0
            JsonToken.NULL -> {
                nextNull()
                0
            }
            else -> {
                skipValue()
                0
            }
        }
    }

    private fun JsonReader.nextHexOrIntSafe(): Int? {
        return when (peek()) {
            JsonToken.NUMBER -> try {
                nextInt()
            } catch (_: Throwable) {
                nextDouble().toInt()
            }
            JsonToken.STRING -> {
                val raw = nextString().trim()
                if (raw.startsWith("0x", ignoreCase = true)) {
                    raw.substring(2).toIntOrNull(16)
                } else {
                    raw.toIntOrNull()
                }
            }
            JsonToken.NULL -> {
                nextNull()
                null
            }
            else -> {
                skipValue()
                null
            }
        }
    }
}
