package com.example.domain

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class DeviceContact(
    val id: String,
    val name: String,
    val phoneNumber: String?
)

class ContactResolver(private val context: Context) {

    fun resolveContactName(titleOrNumber: String): String {
        val trimmed = titleOrNumber.trim()
        if (trimmed.isEmpty()) return "Unknown"

        // If title is already a non-numeric display name (e.g. "Alice"), return it
        if (!trimmed.matches(Regex("""^[\d\s+\-()]+$"""))) {
            return trimmed
        }

        // Check if READ_CONTACTS permission is granted
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                    .appendPath(trimmed)
                    .build()
                val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNull_or_blank()) return name
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to raw phone number
            }
        }

        return trimmed
    }

    fun fetchAllDeviceContacts(): List<DeviceContact> {
        val list = mutableListOf<DeviceContact>()
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return emptyList()

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                val seen = mutableSetOf<String>()
                while (it.moveToNext()) {
                    val name = if (nameCol != -1) it.getString(nameCol) else null
                    val number = if (numCol != -1) it.getString(numCol) else null
                    val rawId = if (idCol != -1) it.getString(idCol) else java.util.UUID.randomUUID().toString()
                    val displayName = name?.trim() ?: number?.trim() ?: continue
                    val cleanNum = number?.trim() ?: ""
                    val uniqueId = "${rawId}_${cleanNum}"
                    val key = "$displayName|$cleanNum"

                    if (seen.add(key)) {
                        list.add(DeviceContact(id = uniqueId, name = displayName, phoneNumber = if (cleanNum.isBlank()) null else cleanNum))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return list
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.isBlank()
    }
}
