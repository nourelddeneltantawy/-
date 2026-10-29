package com.example.data.model

data class ContactInfo(
    val engineerName: String = "م / نور الدين مصطفى الطنطاوي",
    val titleSubtitle: String = "مهندس صيانة وبرمجة كروت ومواتير الغسالات",
    val centerName: String = "مركز البراء إلكترونيك للصيانة المتطورة",
    val address: String = "طنطا الكورنيش، شارع علي اللمعي - بجوار كافيه بلازا القديم",
    val phoneNumber: String = "01018717141",
    val whatsappNumber: String = "201018717141",
    val locationUrl: String = "https://maps.google.com/?q=30.7865,31.0004",
    val facebookUrl: String = "https://facebook.com/AlBaraa01018717141Electronic"
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "engineerName" to engineerName,
            "titleSubtitle" to titleSubtitle,
            "centerName" to centerName,
            "address" to address,
            "phoneNumber" to phoneNumber,
            "whatsappNumber" to whatsappNumber,
            "locationUrl" to locationUrl,
            "facebookUrl" to facebookUrl
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): ContactInfo {
            return ContactInfo(
                engineerName = map["engineerName"] as? String ?: "م / نور الدين مصطفى الطنطاوي",
                titleSubtitle = map["titleSubtitle"] as? String ?: "مهندس صيانة وبرمجة كروت ومواتير الغسالات",
                centerName = map["centerName"] as? String ?: "مركز البراء إلكترونيك للصيانة المتطورة",
                address = map["address"] as? String ?: "طنطا الكورنيش، شارع علي اللمعي - بجوار كافيه بلازا القديم",
                phoneNumber = map["phoneNumber"] as? String ?: "01018717141",
                whatsappNumber = map["whatsappNumber"] as? String ?: "201018717141",
                locationUrl = map["locationUrl"] as? String ?: "https://maps.google.com/?q=30.7865,31.0004",
                facebookUrl = map["facebookUrl"] as? String ?: "https://facebook.com/AlBaraa01018717141Electronic"
            )
        }
    }
}
