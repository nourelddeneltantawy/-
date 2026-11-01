package com.example.data.model

import java.util.UUID

/**
 * Programming Tools & PC Software model.
 */
data class ProgrammerSoftware(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val version: String = "v1.0",
    val fileUrl: String = "",
    val fileSizeText: String = "",
    val fileType: String = "EXE", // "EXE", "ZIP", "APK", "RAR", "LINK"
    val iconUrl: String = "",
    val requiresLicense: Boolean = false,
    val isLocked: Boolean = false,
    val createdTimestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to name,
            "description" to description,
            "version" to version,
            "fileUrl" to fileUrl,
            "fileSizeText" to fileSizeText,
            "fileType" to fileType,
            "iconUrl" to iconUrl,
            "requiresLicense" to requiresLicense,
            "isLocked" to isLocked,
            "createdTimestamp" to createdTimestamp
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): ProgrammerSoftware {
            return ProgrammerSoftware(
                id = id,
                name = map["name"] as? String ?: "",
                description = map["description"] as? String ?: "",
                version = map["version"] as? String ?: "v1.0",
                fileUrl = map["fileUrl"] as? String ?: "",
                fileSizeText = map["fileSizeText"] as? String ?: "",
                fileType = map["fileType"] as? String ?: "EXE",
                iconUrl = map["iconUrl"] as? String ?: "",
                requiresLicense = (map["requiresLicense"] as? Boolean) ?: false,
                isLocked = (map["isLocked"] as? Boolean) ?: false,
                createdTimestamp = (map["createdTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

/**
 * Firmware Folder (Desktop-like Folder navigation).
 */
data class FirmwareFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val brand: String = "",
    val iconName: String = "folder",
    val isLocked: Boolean = true, // Locked for subscribers by default
    val orderIndex: Int = 0
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to name,
            "description" to description,
            "brand" to brand,
            "iconName" to iconName,
            "isLocked" to isLocked,
            "orderIndex" to orderIndex
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): FirmwareFolder {
            return FirmwareFolder(
                id = id,
                name = map["name"] as? String ?: "",
                description = map["description"] as? String ?: "",
                brand = map["brand"] as? String ?: "",
                iconName = map["iconName"] as? String ?: "folder",
                isLocked = (map["isLocked"] as? Boolean) ?: true,
                orderIndex = (map["orderIndex"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

/**
 * Microcontroller / Processor Firmware Dump.
 */
data class FirmwareDump(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val folderId: String = "",
    val processorType: String = "", // e.g. "R5F21334", "STM32F103", "ATmega64", "M30260"
    val boardPartNumber: String = "", // e.g. "EBR78263701"
    val applianceModel: String = "", // e.g. "غسالة إل جي 10 كيلو دايركت درايف"
    val fileUrl: String = "",
    val fileType: String = "BIN", // "BIN", "HEX", "DUMP", "ZIP"
    val previewImageUrl: String = "",
    val notes: String = "",
    val requiresLicense: Boolean = true,
    val createdTimestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "title" to title,
            "folderId" to folderId,
            "processorType" to processorType,
            "boardPartNumber" to boardPartNumber,
            "applianceModel" to applianceModel,
            "fileUrl" to fileUrl,
            "fileType" to fileType,
            "previewImageUrl" to previewImageUrl,
            "notes" to notes,
            "requiresLicense" to requiresLicense,
            "createdTimestamp" to createdTimestamp
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): FirmwareDump {
            return FirmwareDump(
                id = id,
                title = map["title"] as? String ?: "",
                folderId = map["folderId"] as? String ?: "",
                processorType = map["processorType"] as? String ?: "",
                boardPartNumber = map["boardPartNumber"] as? String ?: "",
                applianceModel = map["applianceModel"] as? String ?: "",
                fileUrl = map["fileUrl"] as? String ?: "",
                fileType = map["fileType"] as? String ?: "BIN",
                previewImageUrl = map["previewImageUrl"] as? String ?: "",
                notes = map["notes"] as? String ?: "",
                requiresLicense = (map["requiresLicense"] as? Boolean) ?: true,
                createdTimestamp = (map["createdTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

/**
 * Programmer to Board Connection diagram.
 */
data class ProgrammerConnection(
    val id: String = UUID.randomUUID().toString(),
    val programmerName: String = "", // e.g. "RT809H", "CH341A", "ST-Link V2", "TL866"
    val boardName: String = "", // e.g. "كارتة غسالة إل جي Direct Drive"
    val boardImageUrl: String = "",
    val programmerImageUrl: String = "",
    val wiringInstructions: String = "",
    val pins: List<ConnectionPinItem> = emptyList(),
    val voltageNote: String = "3.3V DC",
    val createdTimestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "programmerName" to programmerName,
            "boardName" to boardName,
            "boardImageUrl" to boardImageUrl,
            "programmerImageUrl" to programmerImageUrl,
            "wiringInstructions" to wiringInstructions,
            "pins" to pins.map { it.toMap() },
            "voltageNote" to voltageNote,
            "createdTimestamp" to createdTimestamp
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(id: String, map: Map<String, Any?>): ProgrammerConnection {
            val pinsList = (map["pins"] as? List<Map<String, Any?>>)?.map {
                ConnectionPinItem.fromMap(it)
            } ?: emptyList()

            return ProgrammerConnection(
                id = id,
                programmerName = map["programmerName"] as? String ?: "",
                boardName = map["boardName"] as? String ?: "",
                boardImageUrl = map["boardImageUrl"] as? String ?: "",
                programmerImageUrl = map["programmerImageUrl"] as? String ?: "",
                wiringInstructions = map["wiringInstructions"] as? String ?: "",
                pins = pinsList,
                voltageNote = map["voltageNote"] as? String ?: "3.3V DC",
                createdTimestamp = (map["createdTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

data class ConnectionPinItem(
    val programmerPin: String = "", // e.g. "VCC (3.3V)", "GND", "CLK", "DIO", "TX", "RX"
    val boardPin: String = "", // e.g. "نقطة TP1", "الطرف رقم 4 في الفلاشة"
    val wireColor: String = "أحمر",
    val colorHex: String = "#EF4444",
    val note: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "programmerPin" to programmerPin,
            "boardPin" to boardPin,
            "wireColor" to wireColor,
            "colorHex" to colorHex,
            "note" to note
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): ConnectionPinItem {
            return ConnectionPinItem(
                programmerPin = map["programmerPin"] as? String ?: "",
                boardPin = map["boardPin"] as? String ?: "",
                wireColor = map["wireColor"] as? String ?: "أحمر",
                colorHex = map["colorHex"] as? String ?: "#EF4444",
                note = map["note"] as? String ?: ""
            )
        }
    }
}

/**
 * Unique Activation / License Key for clients.
 */
data class LicenseKeyRecord(
    val id: String = "", // Same as key
    val licenseKey: String = "",
    val clientName: String = "",
    val clientPhone: String = "",
    val targetScope: String = "ALL_FIRMWARE", // "ALL_FIRMWARE", or specific itemId
    val targetScopeName: String = "جميع السوفتات والبرامج",
    val isActivated: Boolean = false,
    val activatedDeviceId: String = "",
    val createdTimestamp: Long = System.currentTimeMillis(),
    val activatedTimestamp: Long = 0L,
    val notes: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id.ifBlank { licenseKey },
            "licenseKey" to licenseKey,
            "clientName" to clientName,
            "clientPhone" to clientPhone,
            "targetScope" to targetScope,
            "targetScopeName" to targetScopeName,
            "isActivated" to isActivated,
            "activatedDeviceId" to activatedDeviceId,
            "createdTimestamp" to createdTimestamp,
            "activatedTimestamp" to activatedTimestamp,
            "notes" to notes
        )
    }

    companion object {
        fun fromMap(id: String, map: Map<String, Any?>): LicenseKeyRecord {
            return LicenseKeyRecord(
                id = id,
                licenseKey = map["licenseKey"] as? String ?: id,
                clientName = map["clientName"] as? String ?: "",
                clientPhone = map["clientPhone"] as? String ?: "",
                targetScope = map["targetScope"] as? String ?: "ALL_FIRMWARE",
                targetScopeName = map["targetScopeName"] as? String ?: "جميع السوفتات والبرامج",
                isActivated = (map["isActivated"] as? Boolean) ?: false,
                activatedDeviceId = map["activatedDeviceId"] as? String ?: "",
                createdTimestamp = (map["createdTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                activatedTimestamp = (map["activatedTimestamp"] as? Number)?.toLong() ?: 0L,
                notes = map["notes"] as? String ?: ""
            )
        }
    }
}
