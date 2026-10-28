package com.pantelisstampoulis.androidtemplateproject

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withoutName
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

/**
 * CLAUDE.md states that mappers declare what they map in their type. That rule lived only in
 * prose until this test existed, which is why [PR #14][pr] merged a mapper that violated it —
 * the review caught it, nothing enforced it, and it survived the merge.
 *
 * The rule takes the name at its word: a class called `*Mapper` maps one model to another and
 * says so by implementing a mapper interface. Anything doing a different job should carry a
 * different name rather than an exemption here — `ErrorClassifier` is the worked example,
 * since it turns a `NetworkResult` envelope into an error category rather than mapping model
 * to model.
 *
 * The interface is matched by its `Mapper` suffix rather than against a fixed list, because a
 * mapper interface does not have to live in `:architecture:mapper`. That module holds only the
 * boundaries that outlive an implementation choice; a mapping between a `DbModel` and a
 * library-specific type belongs to the module implementing it.
 *
 * [pr]: https://github.com/pantstamp/ai-dev-pipeline/pull/14
 */
@Suppress("ConstPropertyName")
class MapperKonsistTest {

    @Test
    fun `Classes named '*Mapper' should implement a mapper interface`() {
        Konsist
            .scopeFromProduction()
            .classes()
            .withNameEndingWith(suffix = MapperSuffix)
            .withoutName(*MapperHolders)
            .assertTrue { declaration ->
                // A parent's name carries its type arguments, e.g.
                // "DbToDomainMapper<WatchedMovieDbModel, WatchedMovie>", so compare on the
                // part before '<' rather than the whole string.
                val parents = declaration.parents().map { it.name.substringBefore(delimiter = '<') }
                println("Class: ${declaration.name}, parents: $parents.")
                parents.any { parentName -> parentName.endsWith(MapperSuffix) }
            }
    }

    companion object {
        private const val MapperSuffix = "Mapper"

        /**
         * `Mappers` is a holder that groups the real mappers for injection. It maps nothing
         * itself, so the rule does not apply to it.
         */
        private val MapperHolders = arrayOf("Mappers")
    }
}
