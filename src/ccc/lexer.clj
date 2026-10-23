(ns ccc.lexer
  "Tokenizer. Produces a vector of token maps:
   {:kind :ident|:num|:pnum|:str|:char|:punct|:eof
    :text original text, :line n, :file name,
    :bol true if first token on a line, :space true if preceded by whitespace}
   Numbers are kept as :pnum (preprocessing numbers) and converted by convert-number."
  (:require [clojure.string :as str]))

(defn error [file line msg]
  (throw (ex-info (str file ":" line ": error: " msg) {:type :compile-error})))

(def puncts
  ;; longest first
  ["<<=" ">>=" "..." "->" "++" "--" "<<" ">>" "<=" ">=" "==" "!=" "&&" "||"
   "+=" "-=" "*=" "/=" "%=" "&=" "|=" "^=" "##"
   "[" "]" "(" ")" "{" "}" "." "&" "*" "+" "-" "~" "!" "/" "%" "<" ">" "^" "|"
   "?" ":" ";" "=" "," "#"])

(defn- ident-start? [c] (or (Character/isLetter (char c)) (= c \_) (= c \$)))
(defn- ident-char? [c] (or (Character/isLetterOrDigit (char c)) (= c \_) (= c \$)))

(defn- hexval [c]
  (Character/digit (char c) 16))

(defn read-escape
  "s is string, i index just after the backslash. Returns [value new-index]."
  [^String s i file line]
  (let [n (.length s)
        c (.charAt s i)]
    (cond
      (<= 0 (Character/digit c 8))
      (loop [j i v 0 k 0]
        (if (and (< j n) (< k 3) (<= 0 (Character/digit (.charAt s j) 8)))
          (recur (inc j) (+ (* v 8) (Character/digit (.charAt s j) 8)) (inc k))
          [v j]))
      (= c \x)
      (loop [j (inc i) v 0]
        (if (and (< j n) (>= (hexval (.charAt s j)) 0))
          (recur (inc j) (+ (* v 16) (hexval (.charAt s j))))
          (do (when (= j (inc i)) (error file line "invalid hex escape"))
              [v j])))
      :else
      [(case c
         \a 7 \b 8 \t 9 \n 10 \v 11 \f 12 \r 13 \e 27
         (int c))
       (inc i)])))

(defn tokenize
  "Tokenize source string. Returns vector of tokens ending with :eof."
  [^String src file]
  (let [src (-> src (str/replace "\r\n" "\n"))
        ;; handle line splices: backslash-newline. Keep line numbers by recording.
        n (.length src)
        out (transient [])]
    (loop [i 0 line 1 bol true space false]
      (if (>= i n)
        (persistent! (conj! out {:kind :eof :text "" :line line :file file :bol true :space space}))
        (let [c (.charAt src i)]
          (cond
            ;; line splice
            (and (= c \\) (< (inc i) n) (= (.charAt src (inc i)) \newline))
            (recur (+ i 2) (inc line) bol space)

            (= c \newline) (recur (inc i) (inc line) true false)

            (or (= c \space) (= c \tab) (= c \formfeed) (= c (char 11)) (= c \return))
            (recur (inc i) line bol true)

            ;; comments
            (and (= c \/) (< (inc i) n) (= (.charAt src (inc i)) \/))
            (let [j (loop [j i]
                      (cond (>= j n) j
                            (and (= (.charAt src j) \\) (< (inc j) n) (= (.charAt src (inc j)) \newline)) (recur (+ j 2))
                            (= (.charAt src j) \newline) j
                            :else (recur (inc j))))
                  nl (count (filter #(= % \newline) (subs src i j)))]
              (recur j (+ line nl) bol true))

            (and (= c \/) (< (inc i) n) (= (.charAt src (inc i)) \*))
            (let [j (.indexOf src "*/" (int (+ i 2)))]
              (when (neg? j) (error file line "unterminated comment"))
              (let [nl (count (filter #(= % \newline) (subs src i j)))]
                (recur (+ j 2) (+ line nl) bol true)))

            ;; numbers (pp-number)
            (or (Character/isDigit c)
                (and (= c \.) (< (inc i) n) (Character/isDigit (.charAt src (inc i)))))
            (let [j (loop [j (inc i)]
                      (if (>= j n) j
                          (let [d (.charAt src j)]
                            (cond
                              (and (#{\e \E \p \P} d) (< (inc j) n) (#{\+ \-} (.charAt src (inc j))))
                              (recur (+ j 2))
                              (or (ident-char? d) (= d \.)) (recur (inc j))
                              :else j))))]
              (conj! out {:kind :pnum :text (subs src i j) :line line :file file :bol bol :space space})
              (recur j line false false))

            ;; string / char literals (with optional L/u/U/u8 prefix)
            (or (= c \") (= c \')
                (and (#{\L \U \u} c) (< (inc i) n) (#{\" \'} (.charAt src (inc i))))
                (and (= c \u) (< (+ i 2) n) (= (.charAt src (inc i)) \8) (= (.charAt src (+ i 2)) \")))
            (let [pre-len (cond (#{\" \'} c) 0
                                (= (.charAt src (inc i)) \8) 2
                                :else 1)
                  prefix (subs src i (+ i pre-len))
                  q (.charAt src (+ i pre-len))
                  start (+ i pre-len 1)
                  j (loop [j start]
                      (cond (>= j n) (error file line "unterminated literal")
                            (= (.charAt src j) \newline) (error file line "unterminated literal")
                            (= (.charAt src j) \\) (recur (+ j 2))
                            (= (.charAt src j) q) j
                            :else (recur (inc j))))
                  text (subs src i (inc j))]
              (conj! out {:kind (if (= q \") :str :char) :text text :prefix prefix
                          :line line :file file :bol bol :space space})
              (recur (inc j) line false false))

            (ident-start? c)
            (let [j (loop [j (inc i)]
                      (if (and (< j n) (ident-char? (.charAt src j))) (recur (inc j)) j))]
              (conj! out {:kind :ident :text (subs src i j) :line line :file file :bol bol :space space})
              (recur j line false false))

            :else
            (let [p (some (fn [p] (when (.startsWith src ^String p (int i)) p)) puncts)]
              (if p
                (do (conj! out {:kind :punct :text p :line line :file file :bol bol :space space})
                    (recur (+ i (count p)) line false false))
                (error file line (str "stray character '" c "'"))))))))))

;; ---------------------------------------------------------------------------
;; Literal conversion

(defn decode-string
  "Decode the body of a string literal token. Returns vector of byte/char values
   (plain strings: the source bytes; wide strings: code points)."
  [tok]
  (let [^String text (:text tok)
        pre (count (:prefix tok ""))
        s (subs text (inc pre) (dec (count text)))
        n (count s)
        wide? (not (or (= "" (:prefix tok "")) (= "u8" (:prefix tok))))]
    (loop [i 0 acc (transient [])]
      (if (>= i n)
        (persistent! acc)
        (let [c (.charAt s i)]
          (if (= c \\)
            (let [[v j] (read-escape s (inc i) (:file tok) (:line tok))]
              (recur j (conj! acc (if wide? v (bit-and v 0xff)))))
            (if (or (not wide?) (< (int c) 128))
              ;; source is read as ISO-8859-1, so each char is one source byte
              (recur (inc i) (conj! acc (bit-and (int c) 0xff)))
              ;; wide literal: decode the run of non-ASCII bytes as UTF-8
              (let [j (loop [j i] (if (and (< j n) (>= (int (.charAt s j)) 128)) (recur (inc j)) j))
                    bs (byte-array (map #(unchecked-byte (int %)) (subs s i j)))
                    ^String u (String. ^bytes bs "UTF-8")]
                (recur j (reduce conj! acc (iterator-seq (.iterator (.boxed (.codePoints u))))))))))))))

(defn char-value
  "Value of a character constant token (as int, char is signed)."
  [tok]
  (let [vals (decode-string tok)]
    (when (empty? vals) (error (:file tok) (:line tok) "empty character constant"))
    (if (= "" (:prefix tok ""))
      (if (= 1 (count vals))
        (let [v (first vals)] (if (> v 127) (- v 256) v))
        ;; multi-char constant: gcc semantics
        (let [v (reduce (fn [a b] (bit-or (bit-shift-left a 8) (bit-and b 0xff))) 0 vals)
              v (bit-and v 0xffffffff)]
          (if (> v 0x7fffffff) (- v 0x100000000) v)))
      (first vals))))
