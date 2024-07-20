//
// Created by lizhi on 2024/7/20.
//

#ifndef AUTOLUA_MLOG_H
#define AUTOLUA_MLOG_H
#include <android/log.h>
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "luajni", __VA_ARGS__)

#endif //AUTOLUA_MLOG_H
