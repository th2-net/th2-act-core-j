package com.exactpro.th2.act.core.dsl

import com.exactpro.th2.common.grpc.Message

interface IResultBuilder {
    fun onFailure(function: (Message, Message) -> Unit)

    fun setMessage(msg: Message): IResultBuilder

    fun setErrorReason(errorMessage: Message): IResultBuilder

    fun failure(msg: Message): IResultBuilder

    fun success(): IResultBuilder

    fun reason(s: String): IResultBuilder
}