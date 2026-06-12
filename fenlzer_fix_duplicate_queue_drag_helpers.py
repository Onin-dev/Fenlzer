#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: python3 fenlzer_fix_duplicate_queue_drag_helpers.py /path/to/Fenlzer")
        return 2

    root = Path(sys.argv[1]).resolve()
    queue_file = root / "app/src/main/java/com/fenl/fenlzer/ui/queue/QueueScreen.kt"

    if not queue_file.exists():
        print(f"ERROR: Cannot find {queue_file}")
        return 1

    text = queue_file.read_text()

    # Remove every existing local maxDragUpPx/maxDragDownPx pair. The previous
    # patches inserted duplicates, which causes Kotlin overload conflicts.
    text, removed = re.subn(
        r"""
        \n\s*fun\s+maxDragUpPx\(\):\s*Float\s*=\s*-dragStartIndex\s*\*\s*rowHeightPx
        \s*
        \n\s*fun\s+maxDragDownPx\(\):\s*Float\s*=\s*\(baseItems\.lastIndex\s*-\s*dragStartIndex\)\s*\*\s*rowHeightPx
        \s*
        """,
        "\n",
        text,
        flags=re.VERBOSE,
    )

    helper = """    fun maxDragUpPx(): Float = -dragStartIndex * rowHeightPx

    fun maxDragDownPx(): Float = (baseItems.lastIndex - dragStartIndex) * rowHeightPx

"""

    # Reinsert exactly one helper pair immediately before updateDragOffsetSafely
    # if that helper exists; otherwise put it before dragBy.
    if "fun updateDragOffsetSafely(newOffset: Float)" in text:
        text = text.replace(
            "    fun updateDragOffsetSafely(newOffset: Float)",
            helper + "    fun updateDragOffsetSafely(newOffset: Float)",
            1,
        )
    elif "    fun dragBy(deltaY: Float)" in text:
        text = text.replace(
            "    fun dragBy(deltaY: Float)",
            helper + "    fun dragBy(deltaY: Float)",
            1,
        )
    else:
        print("ERROR: Could not find updateDragOffsetSafely() or dragBy() insertion point.")
        return 1

    queue_file.write_text(text)

    print(f"Removed duplicate maxDragUpPx/maxDragDownPx helper block(s): {removed}")
    print("Inserted exactly one helper pair in QueueScreen.kt.")
    print("Next: make debug")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
