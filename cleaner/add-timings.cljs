#!/usr/bin/env planck

(require '[planck.core :refer [slurp *in*]]
         '[planck.shell :refer [sh]]
         '[clojure.string :refer [includes? join split]])

(defn json->clj
  [json]
  (-> json
      JSON.parse
      (js->clj :keywordize-keys true)))

(defn clj->json
  [clj]
  (-> clj
      clj->js
      JSON.stringify))

(def timing-string "7.00pm")
(def timing 1900)
(def interval-string "\n7.00am - 1.00pm")
(def intervals-string "7.00am - 1.00pm,\n6.00pm - 12.00am")
(def timing-string-intervals [["7.00am" "1.00pm"] ["6.00pm" "12.00am"]])
(def timing-intervals [[700 1300] [1800 2400]])
(def mon-fri-item-string "Mon, Tue, Thu & Fri:\n9.00am - 12.30pm,\n2.00pm - 4.30pm")
(def mon-fri-string "Mon, Tue, Thu & Fri:\n9.00am - 12.30pm,\n2.00pm - 4.30pm\n\nWed:\n9.00am - 12.30pm")

;; (def *old-mon-fri-pattern* #"(?s)(.+:.+(?:am|pm))(.+:.+)")
(def *mon-fri-pattern* #"(?s).+?:(?:[^:]+(?:am|pm))")
(def *timing-pattern* #"(\d{1,2})[.:](\d{2})(am|pm)")
(def *days* ["Mon" "Tue" "Wed" "Thu" "Fri"])
(def *days-pattern* (re-pattern (join "|" *days*)))

;; (re-find (re-pattern (join "|" days)) mf2)

(defn hours
  [hh meridiem]
  (let [hh-int (mod (js/parseInt hh) 12)]
    (if (= "pm" meridiem)
      (+ hh-int 12)
      hh-int)))

(defn minutes
  [mm]
  (js/parseInt mm))

(defn timing
  [timing-string]
  (when-let [[_ hh mm meridiem] (re-find *timing-pattern* timing-string)]
    (+ (minutes mm) (* (hours hh meridiem) 100))))

(defn timing-string-intervals
  [intervals-string]
  (if (includes? intervals-string "24 H")
    [["12.00am" "11.59pm"]]
    (map #(split %1 "-") (split intervals-string ","))))

(defn timing-intervals
  [intervals-string]
  (for [interval (timing-string-intervals intervals-string)]
    (map timing interval)))

(defn mon-fri-item-intervals
  [mon-fri-item-string]
  (let [[days intervals-string] (split mon-fri-item-string ":")
        result (timing-intervals intervals-string)]
    (map (fn [day] (when (includes? days day) result)) *days*)))

(defn mon-fri-timing-intervals
  [mon-fri-string]
  (if-not (re-find *days-pattern* mon-fri-string)
    (repeat 5 (timing-intervals mon-fri-string))
    (let [mon-fri-item-strings (re-seq *mon-fri-pattern* mon-fri-string)
          x (map mon-fri-item-intervals mon-fri-item-strings)]
      (apply map (fn [& args] (first (drop-while nil? args))) x))))

(defn clinic->timings
  [clinic]
  (let [{monFri :monFri sun :sun sat :sat publicHolidays :publicHolidays} clinic
        [mon tue wed thu fri] (mon-fri-timing-intervals monFri)]
    {:days [(timing-intervals sun) mon tue wed thu fri (timing-intervals sat)]
     :publicHolidays (timing-intervals publicHolidays)}))

(defn add-timings
  [clinic]
  (assoc clinic :timings (clinic->timings clinic)))

(def in (-> *in*
            slurp
            json->clj))

(print (->> in
            (map add-timings)
            clj->json))
