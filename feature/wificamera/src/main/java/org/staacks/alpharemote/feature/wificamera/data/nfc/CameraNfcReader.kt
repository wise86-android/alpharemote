package org.staacks.alpharemote.feature.wificamera.data.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Parcelable
import android.util.Log

/**
 * Pulls a camera's credentials out of an NFC tap.
 *
 * Separate from [SonyNfcTagParser] so the TLV parsing stays free of Android types and testable on
 * the JVM; everything here is intent plumbing.
 */
object CameraNfcReader {

    private const val TAG = "CameraNfcReader"

    /** True when this intent came from touching a Sony camera. */
    fun isCameraTap(intent: Intent?): Boolean =
        intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED &&
            intent.type == SonyNfcTagParser.MIME_TYPE

    /**
     * Reads the tag, or null if the intent carries no usable Sony record.
     *
     * Only record 0 of the first message is read — that is where the camera writes its TLV list.
     */
    fun read(intent: Intent): CameraNfcTag? {
        val messages = intent.ndefMessages() ?: return null

        logRecords(messages)

        val payload = messages
            .firstNotNullOfOrNull { message -> message.records.firstOrNull()?.payload }
            ?: return null

        return SonyNfcTagParser.parse(payload).also { tag ->
            if (tag == null) Log.w(TAG, "Tapped tag was not a Sony TLV payload")
        }
    }

    /**
     * Names every record on the tag.
     *
     * Worth having because of the Android Application Record: Sony writes one naming their own
     * package, and it is what makes a tap on a closed app open the Play Store instead of this one.
     * Seeing `android.com:pkg` here is the confirmation that no intent filter can win that race.
     */
    private fun logRecords(messages: List<NdefMessage>) {
        messages.forEachIndexed { messageIndex, message ->
            message.records.forEachIndexed { recordIndex, record ->
                val type = String(record.type, Charsets.US_ASCII)
                val extra = if (type == ANDROID_APPLICATION_RECORD) {
                    " -> ${String(record.payload, Charsets.US_ASCII)}"
                } else {
                    ""
                }
                Log.i(
                    TAG,
                    "record $messageIndex.$recordIndex tnf=${record.tnf} type=$type " +
                        "${record.payload.size} bytes$extra"
                )
            }
        }
    }

    private const val ANDROID_APPLICATION_RECORD = "android.com:pkg"

    @Suppress("DEPRECATION")
    private fun Intent.ndefMessages(): List<NdefMessage>? {
        // getParcelableArrayExtra's typed overload exists from API 33, but it rejects the
        // Parcelable[] the NFC stack actually puts in the extra, so the untyped read stays.
        val raw: Array<Parcelable>? = getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        return raw?.filterIsInstance<NdefMessage>()?.takeIf { it.isNotEmpty() }
    }
}
