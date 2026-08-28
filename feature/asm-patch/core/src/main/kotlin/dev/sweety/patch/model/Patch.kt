package dev.sweety.patch.model

data class Patch(val fromVersion: String, val toVersion: String, val operations: List<PatchOperation>)
