package top.lizhistudio.annotation.processor.data

data class CommonParameter(val name:String,val type:CommonType)
data class CommonMethod(val name:String,
                        val returnType:CommonType,
                        val parameters:List<CommonParameter>,
                        val toField:Boolean = false,
                        val isStatic:Boolean = false,
                        val alias:String?=null,var order:Int?=null)
fun CommonMethod.indexName():String{
  return this.alias ?: this.name
}