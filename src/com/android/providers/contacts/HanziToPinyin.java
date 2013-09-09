/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.providers.contacts;

import android.text.TextUtils;
import android.util.Log;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Locale;

import libcore.icu.Transliterator;

/**
 * An object to convert Chinese character to its corresponding pinyin string.
 * For characters with multiple possible pinyin string, only one is selected
 * according to ICU Transliterator class. Polyphone is not supported in this
 * implementation.
 */
public class HanziToPinyin {
    private static final String TAG = "HanziToPinyin";

    private static HanziToPinyin sInstance;
    private Transliterator mPinyinTransliterator;
    private Transliterator mAsciiTransliterator;

    private static final Collator COLLATOR = Collator.getInstance(Locale.CHINA);
    private static final String[] MULTINAMES = {
        "\u590f","\u77bf","\u66fe","\u77f3","\u89e3","\u85cf","\u7fdf","\u90fd",
        "\u516d","\u8584","\u8d3e","\u5c45","\u67e5","\u76db","\u5854","\u548c",
        "\u84dd","\u6bb7","\u4e7e","\u9646","\u4e5c","\u961a","\u53f6","\u5f3a",
        "\u6c64","\u4e07","\u6c88","\u4ec7","\u5357","\u5355","\u535c","\u9e1f",
        "\u601d","\u5bfb","\u65bc","\u4f59","\u6d45","\u6d63","\u65e0","\u4fe1",
        "\u8a31","\u9f50","\u4fde","\u82e5", };

    private static final byte[][] MULTIPYS = {
        { 88, 73, 65, 0, 0, 0 }, { 81, 85, 0, 0, 0, 0 }, { 90, 69, 78, 71, 0, 0 },
        { 83, 72, 73, 0, 0, 0 }, { 88, 73, 69, 0, 0, 0 }, { 90, 65, 78, 71, 0, 0 },
        { 90, 72, 65, 73, 0, 0 }, { 68, 85, 0, 0, 0, 0 }, { 76, 85, 0, 0, 0, 0 },
        { 66, 79, 0, 0, 0, 0 }, { 74, 73, 65, 0, 0, 0 }, { 74, 85, 0, 0, 0, 0 },
        { 90, 72, 65, 0, 0, 0 }, { 83, 72, 69, 78, 71, 0 }, { 84, 65, 0, 0, 0, 0 },
        { 72, 69, 0, 0, 0, 0 }, { 76, 65, 78, 0, 0, 0 }, { 89, 73, 78, 0, 0, 0 },
        { 81, 73, 65, 78, 0, 0 }, { 76, 85, 0, 0, 0, 0 }, { 78, 73, 69, 0, 0, 0 },
        { 75, 65, 78, 0, 0, 0 }, { 89, 69, 0, 0, 0, 0 }, { 81, 73, 65, 78, 71, 0 },
        { 84, 65, 78, 71, 0, 0 }, { 87, 65, 78, 0, 0, 0 }, { 83, 72, 69, 78, 0, 0 },
        { 81, 73, 85, 0, 0, 0 }, { 78, 65, 78, 0, 0, 0 }, { 83, 72, 65, 78, 0, 0 },
        { 66, 85, 0, 0, 0, 0 }, { 78, 73, 65, 79, 0, 0 }, { 83, 73, 0, 0, 0, 0 },
        { 88, 85, 78, 0, 0, 0 }, { 89, 85, 0, 0, 0, 0 }, { 89, 85, 0, 0, 0, 0 },
        { 81, 73, 65, 78, 0, 0 }, { 87, 65, 78, 0, 0, 0 }, { 87, 85, 0, 0, 0, 0 },
        { 88, 73, 78, 0, 0, 0 }, { 88, 85, 0, 0, 0, 0 }, { 81, 73, 0, 0, 0, 0 },
        { 89, 85, 0, 0, 0, 0 }, { 82, 85, 79, 0, 0, 0 }, };

    public static class Token {
        /**
         * Separator between target string for each source char
         */
        public static final String SEPARATOR = " ";

        public static final int LATIN = 1;
        public static final int PINYIN = 2;
        public static final int UNKNOWN = 3;

        public Token() {
        }

        public Token(int type, String source, String target) {
            this.type = type;
            this.source = source;
            this.target = target;
        }

        /**
         * Type of this token, ASCII, PINYIN or UNKNOWN.
         */
        public int type;
        /**
         * Original string before translation.
         */
        public String source;
        /**
         * Translated string of source. For Han, target is corresponding Pinyin. Otherwise target is
         * original string in source.
         */
        public String target;
    }

    private HanziToPinyin() {
        try {
            mPinyinTransliterator = new Transliterator("Han-Latin/Names; Latin-Ascii; Any-Upper");
            mAsciiTransliterator = new Transliterator("Latin-Ascii");
        } catch (RuntimeException e) {
            Log.w(TAG, "Han-Latin/Names transliterator data is missing,"
                  + " HanziToPinyin is disabled");
        }
    }

    public boolean hasChineseTransliterator() {
        return mPinyinTransliterator != null;
    }

    public static HanziToPinyin getInstance() {
        synchronized (HanziToPinyin.class) {
            if (sInstance == null) {
                sInstance = new HanziToPinyin();
            }
            return sInstance;
        }
    }

    /**
     * Check if the first name is multi-pinyin
     *
     * @return right pinyin for this first name
     */
    private static String getMPinyin(final String firstName) {
        int offset, cmp;
        cmp = -1;

        for (offset = 0; offset < MULTINAMES.length; offset++) {
            cmp = COLLATOR.compare(firstName, MULTINAMES[offset]);
            if (cmp == 0) {
                break;
            }
        }

        if (cmp != 0) {
            return null;
        } else {
            StringBuilder pinyin = new StringBuilder();
            for (int j = 0; j < MULTIPYS[offset].length && MULTIPYS[offset][j] != 0; j++) {
                pinyin.append((char) MULTIPYS[offset][j]);
            }
            return pinyin.toString();
        }
    }

    private void tokenize(char character, Token token, boolean isFirstName) {
        token.source = Character.toString(character);

        // ASCII
        if (character < 128) {
            token.type = Token.LATIN;
            token.target = token.source;
            return;
        }

        // Extended Latin. Transcode these to ASCII equivalents
        if (character < 0x250 || (0x1e00 <= character && character < 0x1eff)) {
            token.type = Token.LATIN;
            token.target = mAsciiTransliterator == null ? token.source :
                mAsciiTransliterator.transliterate(token.source);
            return;
        }

        token.type = Token.PINYIN;

        if (isFirstName) {
            token.target = getMPinyin(Character.toString(character));
            if (token.target != null) {
                return;
            }
        }

        token.target = mPinyinTransliterator.transliterate(token.source);
        if (TextUtils.isEmpty(token.target) ||
            TextUtils.equals(token.source, token.target)) {
            token.type = Token.UNKNOWN;
            token.target = token.source;
        }
    }

    /**
     * Convert the input to a array of tokens. The sequence of ASCII or Unknown characters without
     * space will be put into a Token, One Hanzi character which has pinyin will be treated as a
     * Token. If there is no Chinese transliterator, the empty token array is returned.
     */
    public ArrayList<Token> get(final String input) {
        ArrayList<Token> tokens = new ArrayList<Token>();
        if (!hasChineseTransliterator() || TextUtils.isEmpty(input)) {
            // return empty tokens.
            return tokens;
        }

        final int inputLength = input.length();
        final StringBuilder sb = new StringBuilder();
        int tokenType = Token.LATIN;
        Token token = new Token();
        boolean firstname;

        // Go through the input, create a new token when
        // a. Token type changed
        // b. Get the Pinyin of current charater.
        // c. current character is space.
        for (int i = 0; i < inputLength; i++) {
            final char character = input.charAt(i);
            if (Character.isSpaceChar(character)) {
                if (sb.length() > 0) {
                    addToken(sb, tokens, tokenType);
                }
            } else {
                tokenize(character, token, i == 0);
                if (token.type == Token.PINYIN) {
                    if (sb.length() > 0) {
                        addToken(sb, tokens, tokenType);
                    }
                    tokens.add(token);
                    token = new Token();
                } else {
                    if (tokenType != token.type && sb.length() > 0) {
                        addToken(sb, tokens, tokenType);
                    }
                    sb.append(token.target);
                }
                tokenType = token.type;
            }
        }
        if (sb.length() > 0) {
            addToken(sb, tokens, tokenType);
        }
        return tokens;
    }

    private void addToken(
            final StringBuilder sb, final ArrayList<Token> tokens, final int tokenType) {
        String str = sb.toString();
        tokens.add(new Token(tokenType, str, str));
        sb.setLength(0);
    }
}
