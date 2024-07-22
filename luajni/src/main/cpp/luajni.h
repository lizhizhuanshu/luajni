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

int luaJniInitContext(JNIEnv*env);
int luaJniReleaseContext(JNIEnv*env);
int luaJniRegister(const char*name, LuaJniInjectMethod method, void* userData);
void* luaJniUnregister(const char*name);
JNIEnv* luaJniGetEnv(lua_State*L);
void luaJniInitLua(lua_State*L, JNIEnv *env);
int luaJniInject(lua_State*L, JNIEnv *env,const char*name);
int luaJniInjectAll(lua_State*L, JNIEnv *env);
int luaJniRegisteredCount();


int64_t luaJniCacheObject(JNIEnv*env, jobject obj);
void luaJniReleaseObject(JNIEnv*env, int64_t id);
#define luaJniTakeObject(env,id) ((jobject)id)
#define luaJniPutBackObject(env,obj)



int luaJniJavaObjectGc(lua_State *L);
int luaJniCatchJavaException(lua_State*L, JNIEnv*env);
void luaJniCatchJavaAndThrowLuaException(lua_State*L, JNIEnv*env);

int luaJniEqualJavaArray(JavaArray* a, const char*className, int level, enum ARRAY_ELEMENT_TYPE elementType);

//return 1 is success, 0 is java exception
#define LUA_PUSH_FIELD(name,type) int luaJniPush##name##Field(lua_State*L, JNIEnv *env, j##type a_##type,jfieldID field)
#define LUA_PUSH_FIELD_X(name) LUA_PUSH_FIELD(name,object); \
LUA_PUSH_FIELD(Static##name,class)
#define LUA_PUSH_FIELD_EX(name) LUA_PUSH_FIELD_X(name); \
LUA_PUSH_FIELD_X(Wrapper##name);

LUA_PUSH_FIELD_EX(Int);
LUA_PUSH_FIELD_EX(Boolean);
LUA_PUSH_FIELD_EX(Byte);
LUA_PUSH_FIELD_EX(Short);
LUA_PUSH_FIELD_EX(Long);
LUA_PUSH_FIELD_EX(Float);
LUA_PUSH_FIELD_EX(Double);
LUA_PUSH_FIELD_EX(Char);
LUA_PUSH_FIELD_X(String);
#undef LUA_PUSH_FIELD_EX
#undef LUA_PUSH_FIELD_X
#undef LUA_PUSH_FIELD

int luaJniPushObjectField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field,const char*className);
int luaJniPushStaticObjectField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field,const char*className);
int luaJniPushArrayField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field,const char*className,int level,
                    enum ARRAY_ELEMENT_TYPE elementType);
int luaJniPushStaticArrayField(lua_State*L, JNIEnv *env, jobject obj,jfieldID field,const char*className,int level,
                         enum ARRAY_ELEMENT_TYPE elementType);


jobject luaJniNewBoolean(JNIEnv*env, jboolean value);
jobject luaJniNewByte(JNIEnv*env, jbyte value);
jobject luaJniNewChar(JNIEnv*env, jchar value);
jobject luaJniNewShort(JNIEnv*env, jshort value);
jobject luaJniNewInt(JNIEnv*env, jint value);
jobject luaJniNewLong(JNIEnv*env, jlong value);
jobject luaJniNewFloat(JNIEnv*env, jfloat value);
jobject luaJniNewDouble(JNIEnv*env, jdouble value);

jboolean luaJniBooleanValue(JNIEnv*env, jobject obj);
jbyte luaJniByteValue(JNIEnv*env, jobject obj);
jshort luaJniShortValue(JNIEnv*env, jobject obj);
jchar luaJniCharValue(JNIEnv*env, jobject obj);
jint luaJniIntValue(JNIEnv*env, jobject obj);
jlong luaJniLongValue(JNIEnv*env, jobject obj);
jfloat luaJniFloatValue(JNIEnv*env, jobject obj);
jdouble luaJniDoubleValue(JNIEnv*env, jobject obj);
#ifdef __cplusplus
};
#endif

#endif //AUTOLUA_LUAJNI_H
