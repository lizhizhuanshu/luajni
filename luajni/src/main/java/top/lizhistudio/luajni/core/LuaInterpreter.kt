package top.lizhistudio.luajni.core

class LuaInterpreter {
  private var nativePtr = create()

  fun execute(script: String): Any? {
    if (nativePtr == 0L) throw IllegalStateException("LuaInterpreter has been destroyed.")
    return execute(nativePtr, script)
  }

  fun register(vararg classes:Class<*>) = register(*classes.map { it.name }.toTypedArray())
  private fun register(vararg names:String):Int{
    var count = 0
    names.forEach {
      if(register(nativePtr, it)){
        count++
      }
    }
    return count
  }

  fun destroy() {
    if(nativePtr != 0L){
      destroy(nativePtr)
      nativePtr = 0
    }
  }

  companion object {
    init {
      System.loadLibrary("engine")
    }
    external fun create(): Long
    external fun register(nativePtr: Long, name: String):Boolean
    external fun destroy(nativePtr: Long)
    external fun execute(nativePtr: Long, script: String):Any?
  }
}