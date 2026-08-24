package io.github.dayboard.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.dayboard.domain.model.Tag
import io.github.dayboard.domain.model.findByName
import io.github.dayboard.domain.model.normalizeTagEmoji
import io.github.dayboard.domain.repository.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The account's tags, once, for everything that uses them.
 *
 * Tasks and notes share one vocabulary: a tag made on a note can be put on a task
 * straight away. Sharing the *data* was never in question - both read the same
 * collection - but each having its own listener was, and it was wrong.
 *
 * A listener ignores the echo of a write made on this device, because the writer
 * has already applied that change and re-applying it would undo whatever came
 * after. With two listeners, a tag created through one of them arrived at the other
 * as exactly that ignorable echo - and Firestore does not send a second snapshot
 * when the server merely acknowledges a write. So the other list never heard about
 * the tag at all until the page was reloaded.
 *
 * One listener has no such gap: whoever creates a tag tells this, and everything
 * reads it from here.
 */
class TagsController(
    private val repository: TagRepository,
    private val scope: CoroutineScope,
) {

    var all: List<Tag> by mutableStateOf(emptyList())
        private set

    /** True once the stored tags have arrived, or once we know there are none. */
    var loaded: Boolean by mutableStateOf(false)
        private set

    private var uid: String? = null
    private var stopListening: (() -> Unit)? = null

    /** Follows one account's tags. Safe to call repeatedly. */
    fun start(uid: String) {
        if (this.uid == uid) return

        stop()
        this.uid = uid
        stopListening = repository.observe(uid) { stored ->
            all = stored
            loaded = true
        }
    }

    /** Detaches, and forgets the previous account's tags. */
    fun stop() {
        stopListening?.invoke()
        stopListening = null
        uid = null
        all = emptyList()
        loaded = false
    }

    /**
     * Returns the tag called [name], making it if there is not one already.
     *
     * A name that exists wins over making a second tag, compared without regard to
     * case or surrounding space. That is friendlier than refusing - the user asked
     * for a tag with that name and they get one - and it is what stops the list
     * filling up with "Work", "work" and "work ".
     *
     * Null only when there is no name to make a tag from, or nobody signed in.
     */
    fun createOrFind(name: String, color: String, emoji: String?): Tag? {
        val trimmed = name.trim().ifEmpty { return null }
        val account = uid ?: return null

        all.findByName(trimmed)?.let { return it }

        val tag = Tag(id = newId(), name = trimmed, color = color, emoji = normalizeTagEmoji(emoji))
        all = all + tag
        scope.launch { repository.save(account, tag) }
        return tag
    }

    /**
     * Changes a tag's name, colour or emoji.
     *
     * Everything carrying it keeps carrying it: tasks and notes hold the tag's id,
     * not a copy of its name, so a rename shows everywhere at once and there is
     * nothing else to update.
     */
    fun update(tagId: String, name: String, color: String, emoji: String?) {
        val trimmed = name.trim().ifEmpty { return }
        val account = uid ?: return
        val existing = all.firstOrNull { it.id == tagId } ?: return

        val updated = existing.copy(
            name = trimmed,
            color = color,
            emoji = normalizeTagEmoji(emoji),
        )

        all = all.map { if (it.id == tagId) updated else it }
        scope.launch { repository.save(account, updated) }
    }

    /**
     * Removes a tag.
     *
     * Taking it off the tasks and notes carrying it is the caller's job, and has to
     * happen too: an item left holding an id that matches no tag is invisible but
     * real, and enough to hide it under a filter that no longer exists.
     */
    fun delete(tagId: String) {
        val account = uid ?: return

        all = all.filterNot { it.id == tagId }
        scope.launch { repository.delete(account, tagId) }
    }
}
