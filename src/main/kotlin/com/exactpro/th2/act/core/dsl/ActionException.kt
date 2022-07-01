package com.exactpro.th2.act.core.dsl

class NoResponseFoundException(cause: String): Exception(cause)

class FailedResponseFoundException(cause: String): Exception(cause)