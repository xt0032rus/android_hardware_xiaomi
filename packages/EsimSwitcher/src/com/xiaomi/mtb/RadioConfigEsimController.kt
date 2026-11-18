/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.mtb

import android.content.Context
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.SIM_TYPE_EMBEDDED
import android.telephony.TelephonyManager.SIM_TYPE_PHYSICAL
import android.telephony.UiccSlotMapping

internal class RadioConfigEsimController(context: Context) {
    private val telephony = checkNotNull(context.getSystemService(TelephonyManager::class.java))
    private val euiccSlots =
        context.resources.getIntArray(com.android.internal.R.array.non_removable_euicc_slots)

    fun getEsimEnabled(): Boolean {
        val (_, type) = getSwitchableSlot()
        return type == SIM_TYPE_EMBEDDED
    }

    fun setEsimEnabled(enabled: Boolean) {
        val (slotIndex, currentType) = getSwitchableSlot()
        val requestedType = if (enabled) SIM_TYPE_EMBEDDED else SIM_TYPE_PHYSICAL
        if (currentType == requestedType) return

        val mappings = telephony.simSlotMapping
        check(mappings.size == telephony.activeModemCount)
        check(mappings.count { it.physicalSlotIndex == slotIndex } == 1)
        check(mappings.all { it.simType == SIM_TYPE_EMBEDDED || it.simType == SIM_TYPE_PHYSICAL })
        telephony.setSimSlotMapping(
            mappings.map { mapping ->
                if (mapping.physicalSlotIndex == slotIndex) {
                    UiccSlotMapping(
                        mapping.portIndex,
                        mapping.physicalSlotIndex,
                        mapping.logicalSlotIndex,
                        requestedType,
                    )
                } else {
                    mapping
                }
            }
        )
    }

    private fun getSwitchableSlot(): Pair<Int, Int> {
        val slots = checkNotNull(telephony.uiccSlotsInfo)
        val slotIndex = euiccSlots.single { index ->
            val supported = slots.getOrNull(index)?.supportedSimTypes
            supported != null && SIM_TYPE_EMBEDDED in supported && SIM_TYPE_PHYSICAL in supported
        }
        val type = slots[slotIndex].simType
        check(type == SIM_TYPE_EMBEDDED || type == SIM_TYPE_PHYSICAL)
        return slotIndex to type
    }
}
