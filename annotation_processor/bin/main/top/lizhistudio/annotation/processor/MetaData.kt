package top.lizhistudio.annotation.processor

interface MetaData {
  fun className():String
  fun fileName():String
  fun injectToLuaMethodName():String
}