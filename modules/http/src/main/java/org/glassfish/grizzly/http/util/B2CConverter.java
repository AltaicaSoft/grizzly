/*
 * Copyright (c) 2010, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.glassfish.grizzly.http.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.utils.Charsets;

/**
 * Efficient conversion of bytes to character .
 *
 * Now uses NIO directly
 */
public class B2CConverter {

    /**
     * Default Logger.
     */
    private static final boolean IS_OLD_IO_MODE = Boolean.getBoolean(B2CConverter.class.getName() + ".blockingMode");

    private static final Logger logger = Grizzly.logger(B2CConverter.class);
    private static final int MAX_NUMBER_OF_BYTES_PER_CHARACTER = 16;

    private CharsetDecoder decoder;
    private final ByteBuffer remainder = ByteBuffer.allocate(MAX_NUMBER_OF_BYTES_PER_CHARACTER);

    // Support old blocking converter
    private B2CConverterBlocking blockingConverter;

    protected B2CConverter() {
        init("US-ASCII");
    }

    /**
     * Create a converter, with bytes going to a byte buffer
     */
    public B2CConverter(String encoding) throws IOException {
        init(encoding);
    }

    protected void init(String encoding) {
        if (IS_OLD_IO_MODE) {
            try {
                blockingConverter = new B2CConverterBlocking(encoding);
            } catch (IOException e) {
                throw new IllegalStateException("Can not initialize blocking converter");
            }
        } else {
            Charset charset = Charsets.lookupCharset(encoding);
            decoder = charset.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
        }
    }

    /**
     * Reset the internal state, empty the buffers. The encoding remain in effect, the internal buffers remain allocated.
     */
    public void recycle() {
        if (IS_OLD_IO_MODE) {
            blockingConverter.recycle();
        }
    }

    /**
     * Convert a buffer of bytes into a chars
     */
    public void convert(ByteChunk bb, CharChunk cb) throws IOException {
        convert(bb, cb, cb.getBuffer().length - cb.getEnd());
    }

    public void convert(ByteChunk bb, CharChunk cb, int limit) throws IOException {
        if (IS_OLD_IO_MODE) {
            blockingConverter.convert(bb, cb, limit);
            return;
        }

        try {
            final int bbAvailable = bb.getEnd() - bb.getStart();
            if (limit > bbAvailable) {
                limit = bbAvailable;
            }

            byte[] barr = bb.getBuffer();
            int boff = bb.getStart();
            ByteBuffer tmp_bb = ByteBuffer.wrap(barr, boff, limit);

            char[] carr = cb.getBuffer();
            int coff = cb.getEnd();
            final int remain = carr.length - coff;
            final int cbLimit = cb.getLimit();
            if (remain < limit && (cbLimit < 0 || cbLimit > carr.length)) {
                cb.makeSpace(limit);
                carr = cb.getBuffer();
                coff = cb.getEnd();
            }

            CharBuffer tmp_cb = CharBuffer.wrap(carr, coff, carr.length - coff);

            if (remainder.position() > 0) {
                flushRemainder(tmp_bb, tmp_cb);
            }

            CoderResult cr = decoder.decode(tmp_bb, tmp_cb, false);
            cb.setEnd(tmp_cb.position());

            while (cr == CoderResult.OVERFLOW) {
                cb.flushBuffer();
                coff = cb.getEnd();
                carr = cb.getBuffer();
                tmp_cb = CharBuffer.wrap(carr, coff, carr.length - coff);
                cr = decoder.decode(tmp_bb, tmp_cb, false);
                cb.setEnd(tmp_cb.position());
            }
            bb.setStart(tmp_bb.position());
            if (tmp_bb.hasRemaining()) {
                remainder.put(tmp_bb);
            }

            if (cr != CoderResult.UNDERFLOW) {
                throw new IOException("Encoding error");
            }
        } catch (IOException ex) {
            int debug = 0;
            if (debug > 0) {
                log("B2CConverter " + ex.toString());
            }
            decoder.reset();
            throw ex;
        }
    }

    // START CR 6309511
    /**
     * Character conversion of a US-ASCII MessageBytes.
     */
    public static void convertASCII(MessageBytes mb) {
        if (IS_OLD_IO_MODE) {
            B2CConverterBlocking.convertASCII(mb);
            return;
        }

        // This is of course only meaningful for bytes
        if (mb.getType() != MessageBytes.T_BYTES) {
            return;
        }

        ByteChunk bc = mb.getByteChunk();
        CharChunk cc = mb.getCharChunk();
        int length = bc.getLength();
        cc.allocate(length, -1);

        // Default encoding: fast conversion
        byte[] bbuf = bc.getBuffer();
        char[] cbuf = cc.getBuffer();
        int start = bc.getStart();
        for (int i = 0; i < length; i++) {
            cbuf[i] = (char) (bbuf[i + start] & 0xff);
        }
        mb.setChars(cbuf, 0, length);

    }
    // END CR 6309511

    public void reset() throws IOException {
        if (IS_OLD_IO_MODE) {
            blockingConverter.reset();
            return;
        }

        if (decoder != null) {
            decoder.reset();
            remainder.clear();
        }
    }

    void log(String s) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "B2CConverter: " + s);
        }
    }

    private void flushRemainder(ByteBuffer tmp_bb, CharBuffer tmp_cb) {
        while (remainder.position() > 0 && tmp_bb.hasRemaining()) {
            remainder.put(tmp_bb.get());
            remainder.flip();
            CoderResult cr = decoder.decode(remainder, tmp_cb, false);
            if (cr == CoderResult.OVERFLOW) {
                // Shouldn't happen, because we allocated required output buffer before
                throw new IllegalStateException("CharChunk is not big enough");
            }

            if (!remainder.hasRemaining()) {
                remainder.clear();
                break;
            }

            remainder.compact();
        }
    }
}
