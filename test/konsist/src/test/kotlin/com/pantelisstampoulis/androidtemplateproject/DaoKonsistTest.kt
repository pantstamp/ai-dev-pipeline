package com.pantelisstampoulis.androidtemplateproject

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withAnnotationNamed
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

/**
 * A Room DAO function that is neither `suspend` nor returns `Flow` is a blocking query. Room
 * will throw if it is called on the main thread, and nothing in the type signature stops a
 * caller from doing exactly that.
 *
 * Raised on [PR #14][pr] and never fixed, which is the reason this rule exists.
 *
 * [pr]: https://github.com/pantstamp/ai-dev-pipeline/pull/14
 */
@Suppress("ConstPropertyName")
class DaoKonsistTest {

    @Test
    fun `Functions in '@Dao' interfaces should be 'suspend' or return 'Flow'`() {
        Konsist
            .scopeFromProduction()
            .interfaces()
            .withAnnotationNamed(DaoAnnotation)
            .assertTrue { declaration ->
                declaration.functions().all { functionDeclaration ->
                    val isSuspend = functionDeclaration.hasSuspendModifier
                    val returnsFlow =
                        functionDeclaration.returnType?.name?.startsWith(FlowType) == true
                    println(
                        "Dao: ${declaration.name}, function: ${functionDeclaration.name}, " +
                            "isSuspend: $isSuspend, returnsFlow: $returnsFlow.",
                    )
                    isSuspend || returnsFlow
                }
            }
    }

    companion object {
        private const val DaoAnnotation = "Dao"
        private const val FlowType = "Flow"
    }
}
