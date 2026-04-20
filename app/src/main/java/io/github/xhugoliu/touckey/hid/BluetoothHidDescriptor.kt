package io.github.xhugoliu.touckey.hid

import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings

object BluetoothHidDescriptor {
    const val KEYBOARD_REPORT_ID = 0x01
    const val MOUSE_REPORT_ID = 0x02
    const val CONSUMER_REPORT_ID = 0x03

    val reportMap: ByteArray =
        byteArrayOf(
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x02.toByte(),
            0xA1.toByte(), 0x01.toByte(),
            0x85.toByte(), MOUSE_REPORT_ID.toByte(),
            0x09.toByte(), 0x01.toByte(),
            0xA1.toByte(), 0x00.toByte(),
            0x05.toByte(), 0x09.toByte(),
            0x19.toByte(), 0x01.toByte(),
            0x29.toByte(), 0x05.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x05.toByte(),
            0x75.toByte(), 0x01.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x03.toByte(),
            0x81.toByte(), 0x01.toByte(),
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x30.toByte(),
            0x09.toByte(), 0x31.toByte(),
            0x09.toByte(), 0x38.toByte(),
            0x15.toByte(), 0x81.toByte(),
            0x25.toByte(), 0x7F.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x95.toByte(), 0x03.toByte(),
            0x81.toByte(), 0x06.toByte(),
            0xC0.toByte(),
            0xC0.toByte(),
            0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x06.toByte(),
            0xA1.toByte(), 0x01.toByte(),
            0x85.toByte(), KEYBOARD_REPORT_ID.toByte(),
            0x05.toByte(), 0x07.toByte(),
            0x19.toByte(), 0xE0.toByte(),
            0x29.toByte(), 0xE7.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x08.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0x05.toByte(), 0x08.toByte(),
            0x19.toByte(), 0x01.toByte(),
            0x29.toByte(), 0x08.toByte(),
            0x95.toByte(), 0x08.toByte(),
            0x75.toByte(), 0x01.toByte(),
            0x91.toByte(), 0x02.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x81.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x06.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x65.toByte(),
            0x05.toByte(), 0x07.toByte(),
            0x19.toByte(), 0x00.toByte(),
            0x29.toByte(), 0x65.toByte(),
            0x81.toByte(), 0x00.toByte(),
            0xC0.toByte(),
            0x05.toByte(), 0x0C.toByte(),
            0x09.toByte(), 0x01.toByte(),
            0xA1.toByte(), 0x01.toByte(),
            0x85.toByte(), CONSUMER_REPORT_ID.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x26.toByte(), 0xFF.toByte(), 0x03.toByte(),
            0x19.toByte(), 0x00.toByte(),
            0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(),
            0x75.toByte(), 0x10.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x81.toByte(), 0x00.toByte(),
            0xC0.toByte(),
        )

    val sdpRecord =
        BluetoothHidDeviceAppSdpSettings(
            "Touckey",
            "Bluetooth keyboard, mouse and media controller",
            "Touckey",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            reportMap,
        )

    val qosOut =
        BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX,
        )
}
