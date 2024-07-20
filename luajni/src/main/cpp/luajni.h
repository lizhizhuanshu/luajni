//
// Created by lizhi on 2024/7/8.
//

#ifndef AUTOLUA_LUAJNI_H
#define AUTOLUA_LUAJNI_H
#ifdef __cplusplus
extern "C" {
#endif
#include <jni.h>
#include <lua.h>
#include <stdint.h>

enum ARRAY_ELEMENT_TYPE{
    ELEMENT_BOOLEAN,
    ELEMENT_BYTE,
    ELEMENT_CHAR,
    ELEMENT_SHORT,
    ELEMENT_INT,
    ELEMENT_LONG,
    ELEMENT_FLOAT,
    ELEMENT_DOUBLE,
    ELEMENT_STRING,
    ELEMENT_OBJECT
};


typedef struct {
    int64_t id;
    int level;
    const char *name;
    enum ARRAY_ELEMENT_TYPE elementType;
} JavaArray;

typedef struct {
    int64_t id;
}JavaObject;


typedef int(*LuaJniInjectMethod)(lua_State*L, JNIEnv *env,void*userData);


int luaJniRegister(const char*name, LuaJniInjectMethod method, void* userData);
void* luaJniUnregister(const char*name);
JNIEnv* luaJniGetEnv(lua_State*L);
void luaJniInitLua(lua_State*L, JNIEnv *env);
int luaJniInject(lua_State*L, JNIEnv *env,const char*name);
int luaJniInjectAll(lua_State*L, JNIEnv *env);
int luaJniRegisteredCount();


int64_t luaJniCacheJavaObject(JNIEnv*env, jobject obj);
void luaJniReleaseJavaObject(JNIEnv*env, int64_t id);
#define luaJniTakeObject(env,id) ((jobject)id)
#define luaJniPutBackObject(env,obj)



int luaJniJavaObjectGc(lua_State *L);
int luaJniCatchJavaException(lua_State*L, JNIEnv*env);
void luaJniCatchJavaAndThrowLuaException(lua_State*L, JNIEnv*env);

int luaJniEqualJavaArray(JavaArray* a, const char*className, int level, enum ARRAY_ELEMENT_TYPE elementType);

//return 1 is success, 0 is java exception
int luaJniPushIntField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field);
int luaJniPushLongField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field);
int luaJniPushShortField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field);
int luaJniPushByteField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field);
int luaJniPushBooleanField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field);
int luaJniPushFloatField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field);
int luaJniPushDoubleField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field);
int luaJniPushCharField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field);
int luaJniPushStringField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field);
int luaJniPushObjectField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field,const char*className);
int luaJniPushArrayField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field,const char*className,int level,
                    enum ARRAY_ELEMENT_TYPE elementType);


#ifdef __cplusplus
};
#endif

#endif //AUTOLUA_LUAJNI_H
