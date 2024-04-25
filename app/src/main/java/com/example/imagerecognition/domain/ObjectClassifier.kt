package com.example.imagerecognition.domain

import android.graphics.Bitmap

interface ObjectClassifier {
    fun classify(bitmap: Bitmap, rotation: Int): List<Classification>
}