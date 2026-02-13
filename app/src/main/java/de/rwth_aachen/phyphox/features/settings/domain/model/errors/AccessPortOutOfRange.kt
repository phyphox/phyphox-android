package de.rwth_aachen.phyphox.features.settings.domain.model.errors

class AccessPortOutOfRange(val intRange: IntRange) : Throwable(message = null)
