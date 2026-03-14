package com.example.nanobot.core.phonecontrol

data class PhoneUiNode(
    val nodeId: String,
    val text: String?,
    val contentDescription: String?,
    val viewIdResourceName: String?,
    val className: String?,
    val packageName: String?,
    val boundsInScreen: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
    val visibleToUser: Boolean,
    val depth: Int
)

data class PhoneUiSnapshot(
    val serviceConnected: Boolean,
    val packageName: String?,
    val activityTitle: String?,
    val capturedAtEpochMs: Long,
    val interactiveNodeCount: Int,
    val returnedNodeCount: Int,
    val nodes: List<PhoneUiNode>,
    val warning: String? = null
)
