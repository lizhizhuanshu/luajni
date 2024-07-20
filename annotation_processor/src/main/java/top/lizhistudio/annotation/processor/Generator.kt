package top.lizhistudio.annotation.processor

interface Generator:MetaData {
  fun headerCode():String
  fun sourceCode():String
}
