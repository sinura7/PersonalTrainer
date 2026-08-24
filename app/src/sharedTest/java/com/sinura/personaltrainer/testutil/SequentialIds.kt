package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.util.IdFactory

/** Deterministic IDs: id-1, id-2, … */
class SequentialIds(private var next: Int = 1) : IdFactory {
    override fun newId(): String = "id-${next++}"
}
