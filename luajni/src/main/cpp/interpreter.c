#include <jni.h>

//
// Created by lizhi on 2024/7/8.
//
#include <lua.h>
#include <lualib.h>
#include <lauxlib.h>
#include <android/log.h>

#include "mlog.h"
#include "luajni.h"
#include "lua_jni_extension.h"

#define SET_ENV(env) (*(JNIEnv**)lua_getextraspace(L)) = env;
#define GET_ENV() (*(JNIEnv**)lua_getextraspace(L))

JNIEXPORT jlong JNICALL
Java_top_lizhistudio_luajni_core_LuaInterpreter_00024Companion_create(JNIEnv *env, jobject thiz) {
    lua_State *L =luaL_newstate();
    luaL_openlibs(L);
    SET_ENV(env);
    luaJniInitLua(L,env);
    return (jlong) L;
}

JNIEXPORT void JNICALL
Java_top_lizhistudio_luajni_core_LuaInterpreter_00024Companion_destroy(JNIEnv *env, jobject thiz,
                                                                   jlong native_ptr) {
    lua_close((lua_State *) native_ptr);
}

JNIEXPORT jobject JNICALL
Java_top_lizhistudio_luajni_core_LuaInterpreter_00024Companion_execute(JNIEnv *env, jobject thiz,
                                                                   jlong native_ptr,
                                                                   jstring script) {
    lua_State *L = (lua_State *) native_ptr;
    SET_ENV(env);
    const char *c_script = (*env)->GetStringUTFChars(env, script, 0);
    int ret = luaL_dostring(L, c_script);
    (*env)->ReleaseStringUTFChars(env, script, c_script);
    if (ret != LUA_OK) {
        jclass clazz = (*env)->FindClass(env,"top/lizhistudio/luajni/core/LuaError");
        (*env)->ThrowNew(env, clazz, lua_tostring(L, -1));
        lua_settop(L,0);
        return NULL;
    }
    int type = lua_type(L, -1);
    jobject result = NULL;
    switch (type) {
        case LUA_TNIL:
            break;
        case LUA_TBOOLEAN:{
            jboolean value = lua_toboolean(L,-1);
            jclass clazz = (*env)->FindClass(env, "java/lang/Boolean");
            jmethodID method = (*env)->GetStaticMethodID(env, clazz, "valueOf", "(Z)Ljava/lang/Boolean;");
            result = (*env)->CallStaticObjectMethod(env, clazz, method, value);
            break;
        }
        case LUA_TNUMBER:{
            if(lua_isinteger(L,-1)){
                jlong value = lua_tointeger(L,-1);
                jclass clazz = (*env)->FindClass(env, "java/lang/Long");
                jmethodID method = (*env)->GetStaticMethodID(env, clazz, "valueOf", "(J)Ljava/lang/Long;");
                result = (*env)->CallStaticObjectMethod(env, clazz, method, value);
            }else{
                jdouble value = lua_tonumber(L,-1);
                jclass clazz = (*env)->FindClass(env, "java/lang/Double");
                jmethodID method = (*env)->GetStaticMethodID(env, clazz, "valueOf", "(D)Ljava/lang/Double;");
                result = (*env)->CallStaticObjectMethod(env, clazz, method, value);
            }
            break;
        }
        case LUA_TSTRING:
            result = (*env)->NewStringUTF(env, lua_tostring(L, -1));
            break;
        default:
            break;
    }
    lua_settop(L,0);
    return result;
}


JNIEXPORT jboolean JNICALL
Java_top_lizhistudio_luajni_core_LuaInterpreter_00024Companion_register(JNIEnv *env, jobject thiz,
                                                                    jlong native_ptr,
                                                                    jstring name) {
    lua_State *L = (lua_State *) native_ptr;
    SET_ENV(env);
    const char *c_name = (*env)->GetStringUTFChars(env, name, 0);
    jboolean  r = luaJniInject(L,env,c_name);
    (*env)->ReleaseStringUTFChars(env, name, c_name);
    LOGD("registered %d \n",luaJniRegisteredCount());
    return r;
}

JNIEXPORT jint JNI_OnLoad(JavaVM * vm, void * reserved)
{
    JNIEnv * env = NULL;
    if ((*vm)->GetEnv(vm,(void**)&env, JNI_VERSION_1_6) != JNI_OK)
        return -1;
    luaJniExtensionRegisterAll(env);
    return  JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM * vm, void * reserved)
{
    JNIEnv * env = NULL;
    if ((*vm)->GetEnv(vm,(void**)&env, JNI_VERSION_1_6) != JNI_OK)
        return;
    luaJniExtensionUnregisterAll(env);
}

