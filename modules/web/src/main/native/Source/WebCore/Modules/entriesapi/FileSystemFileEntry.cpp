/*
 * Copyright (C) 2017 Apple Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY APPLE INC. AND ITS CONTRIBUTORS ``AS IS''
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL APPLE INC. OR ITS CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

#include "config.h"
#include "FileSystemFileEntry.h"

#include "DOMException.h"
#include "DOMFileSystem.h"
#include "ErrorCallback.h"
#include "FileCallback.h"

namespace WebCore {

FileSystemFileEntry::FileSystemFileEntry(ScriptExecutionContext& context, DOMFileSystem& filesystem, const String& virtualPath)
    : FileSystemEntry(context, filesystem, virtualPath)
{
}

void FileSystemFileEntry::file(ScriptExecutionContext& context, Ref<FileCallback>&& successCallback, RefPtr<ErrorCallback>&& errorCallback)
{
    filesystem().getFile(context, *this, [successCallback = WTFMove(successCallback), errorCallback = WTFMove(errorCallback)](auto&& result) {
        if (result.hasException()) {
            if (errorCallback)
                errorCallback->handleEvent(DOMException::create(result.releaseException()));
            return;
        }
        successCallback->handleEvent(result.releaseReturnValue());
    });
}

} // namespace WebCore
