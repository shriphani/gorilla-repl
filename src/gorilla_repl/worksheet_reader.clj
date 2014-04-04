(ns gorilla-repl.worksheet-reader
  (:require [cheshire.core :refer :all]
            [clojure.string :as string]
            [markdown.core :refer :all]
            [instaparse.core :as insta]
            [hiccup.core :refer :all]))

(defn uncomment
  [xs]
  (string/join
   #"\n"
   (map
    (fn [x]
      (subs x 3))
    (string/split-lines xs))))

(def gorilla-worksheet
  (insta/parser
   "worksheet = worksheetHeader segmentWithBlankLine*

    lineEnd = '\\n' / '\\r\\n'

    worksheetHeader = ';; gorilla-repl.fileformat = 1' lineEnd lineEnd

    segmentWithBlankLine = segment lineEnd?

    segment = freeSegment / codeSegment

    freeSegment = freeSegmentOpenTag stringNoDelim? freeSegmentCloseTag

    freeSegmentOpenTag = ';; **' lineEnd

    freeSegmentCloseTag = lineEnd ';; **' lineEnd

    codeSegment = codeSegmentOpenTag stringNoDelim? codeSegmentCloseTag consoleSection? outputSection?

    codeSegmentOpenTag = ';; @@' lineEnd

    codeSegmentCloseTag = lineEnd ';; @@' lineEnd

    outputSection = outputOpenTag stringNoDelim outputCloseTag

    outputOpenTag = ';; =>' lineEnd

    outputCloseTag = lineEnd ';; <=' lineEnd

    consoleSection = consoleOpenTag stringNoDelim consoleCloseTag

    consoleOpenTag = ';; ->' lineEnd

    consoleCloseTag = lineEnd ';; <-' lineEnd

    stringNoDelim = noDelimChar+

    delimiter = freeSegmentOpenTag / freeSegmentCloseTag / codeSegmentOpenTag / codeSegmentCloseTag / outputOpenTag / outputCloseTag / consoleOpenTag / consoleCloseTag

    noDelimChar = !delimiter #'.|\\s'"))

(defn remove-open-close-tags
  [segment open-tag close-tag]
  (filter
   #(and
     (not= (first %) open-tag)
     (not= (first %) close-tag))
   segment))

(defn format-code
  [code]
  (str "<pre>" code "</pre>"))

(defn render-free-segment
  [a-free-segment]
  (html [:div.free-segment a-free-segment]))

(defn read-worksheet
  [worksheet]
  (->> (gorilla-worksheet (slurp worksheet))
       (insta/transform
        {:worksheet (fn [& xs] (rest xs))
         
         :segmentWithBlankLine (fn [& xs] (first xs))
         
         :segment (fn [& xs]
                    (first xs))
         
         :freeSegment (fn [& xs]
                        (render-free-segment
                         (md-to-html-string
                          (uncomment
                           (first
                            (remove-open-close-tags xs
                                                    :freeSegmentOpenTag
                                                    :freeSegmentCloseTag))))))
         
         :codeSegment (fn [& xs]
                        (let [code-segment
                              (remove-open-close-tags xs
                                                      :codeSegmentOpenTag
                                                      :codeSegmentCloseTag)]
                          (if-not (empty? code-segment)
                            (cons (format-code (first code-segment))
                                  (rest code-segment))
                            code-segment)))
         
         :consoleSection (fn [& xs]
                           (uncomment
                            (first
                             (remove-open-close-tags xs
                                                     :consoleOpenTag
                                                     :consoleCloseTag))))
         
         :outputSection (fn [& xs]
                          ((parse-string
                            (uncomment
                             (first
                              (remove-open-close-tags xs
                                                      :outputOpenTag
                                                      :outputCloseTag))))
                           "content"))
         
         :stringNoDelim (fn [& xs]
                          (apply str (map second xs)))})))

