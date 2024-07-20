package top.lizhistudio.annotation.processor.data

data class CommonType(val name:String,val dimensions :Int = 0){
  companion object {
    private val BOOLEAN = CommonType("boolean")
    private val BYTE = CommonType("byte")
    private val CHAR = CommonType("char")
    private val SHORT = CommonType("short")
    private val INT = CommonType("int")
    private val LONG = CommonType("long")
    private val FLOAT = CommonType("float")
    private val DOUBLE = CommonType("double")
    private val STRING = CommonType("java.lang.String")
    fun fromName(name:String):CommonType{
      return when(name){
        "boolean" -> BOOLEAN
        "byte" -> BYTE
        "char" -> CHAR
        "short" -> SHORT
        "int" -> INT
        "long" -> LONG
        "float" -> FLOAT
        "double" -> DOUBLE
        "java.lang.String" -> STRING
        else -> CommonType(name)
      }
    }
    fun fromName(name:String,dimensions: Int):CommonType{
      if(dimensions <0) throw IllegalArgumentException("Dimensions must be greater than 0")
      if(dimensions == 0) return fromName(name)
      return CommonType(name,dimensions)
    }
  }
}

