package top.lizhistudio.annotation.processor

import top.lizhistudio.annotation.processor.data.CommonMethod

interface FunctionContainer {
  fun putFunction(f:CommonMethod)
}