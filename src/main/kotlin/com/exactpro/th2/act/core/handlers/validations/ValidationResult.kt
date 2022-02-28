/*
 * Copyright 2022-2022 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.act.core.handlers.validations

/**
 * Objects of this class store the result of a request validation.
 */
class ValidationResult private constructor(private val validationResult: String?) {
    /**
     * Is `true` if the validated request ended up being invalid.
     */
    val isInvalid: Boolean = (validationResult != null)

    /**
     * Contains a human-readable reason for invalidating the request.
     */
    val invalidationReason: String = (validationResult ?: "Not invalid.")

    companion object {
        private val VALID = ValidationResult(validationResult = null)

        /**
         * Creates a [ValidationResult] marked as being valid.
         */
        @JvmStatic
        fun valid(): ValidationResult = VALID

        /**
         * Creates a [ValidationResult] marked as being invalid.
         *
         * @param reason The reason the request is invalid.
         */
        @JvmStatic
        fun invalid(reason: String): ValidationResult = ValidationResult(validationResult = reason)
    }
}
