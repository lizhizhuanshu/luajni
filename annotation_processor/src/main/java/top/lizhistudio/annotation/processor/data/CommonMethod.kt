package top.lizhistudio.annotation.processor.data

data class CommonParameter(val name:String,val type:CommonType)
data class CommonMethod(val name:String,
                        val returnType:CommonType,
                        val parameters:List<CommonParameter>,
                        val toField:Boolean = false,
                        val isStatic:Boolean = false,
                        val alias:String?=null,
                        val unpack:Array<String>? = null,
                        var order:Int?=null) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CommonMethod

    if (name != other.name) return false
    if (returnType != other.returnType) return false
    if (parameters != other.parameters) return false
    if (toField != other.toField) return false
    if (isStatic != other.isStatic) return false
    if (alias != other.alias) return false
    if (order != other.order) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + returnType.hashCode()
    result = 31 * result + parameters.hashCode()
    result = 31 * result + toField.hashCode()
    result = 31 * result + isStatic.hashCode()
    result = 31 * result + (alias?.hashCode() ?: 0)
    result = 31 * result + (order ?: 0)
    return result
  }
}

fun CommonMethod.indexName():String{
  return this.alias ?: this.name
}