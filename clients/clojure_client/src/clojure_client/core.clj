(ns clojure-client.core
  (:require [clojure.tools.logging :as log])
  (:gen-class))

(def SUITS "HSDC")
(def FACES "23456789TJQKA")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn face-value
  [card]
  (+ 2 (.indexOf FACES (str (first card)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn in-same-suit
  [card1 card2]
  (= (second card1) (second card2)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-cards-in-suit
  [played-cards cards]
  (filter #(in-same-suit % (first played-cards)) cards))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-my-matching-cards
  [client-state]
  (get-cards-in-suit (:played client-state) (:cards client-state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-best-card-to-play
  [valid-cards played-cards]
  (let [my-sorted (sort-by face-value valid-cards)
        in-suit-cards (get-cards-in-suit played-cards played-cards)
        best-played (face-value (apply max-key face-value in-suit-cards))]
    (if (> best-played (face-value (last my-sorted)))
      (first my-sorted) ;;; the best card I have isn't good enough, so throw out my low card
      (loop [current-card (first my-sorted)
             cards (rest my-sorted)]
        (if (> (face-value current-card) best-played)
          current-card
          (recur (first cards) (rest cards)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn send-card
  [client-state]
  (let [card
        (cond
          (empty? (:played client-state))
            ; I am first player, so play my highest card
            (last (sort-by face-value (:cards client-state)))
          (empty? (get-my-matching-cards client-state))
            ; I don't have any cards in suit, so just play my lowest card
            (first (sort-by face-value (:cards client-state)))
          :else
            ; play in suit the highest card that would win, or lowest card in suit if can't win
            (let [valid-cards (get-my-matching-cards client-state)]
              (get-best-card-to-play valid-cards (:played client-state))))]
    (log/info "Playing card " card)
    (println (.trim (format "|RESPONSE|card|%s|END|" card)))
    (flush)
    (assoc client-state :cards (remove #(= % card) (:cards client-state)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn receive-cards
  [client-state line]
  (let [cards (vec (.split (nth (.split line "\\|") 3) " "))]
    (assoc client-state :cards cards)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn card-played
  [client-state line]
  (let [card (nth (.split line "\\|") 4)]
    (update-in client-state [:played] #(conj % card))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn start-trick
  [client-state]
  (assoc client-state :played []))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn run
  [client-state]
  (log/info "Client State: " client-state)
  (let [line (.trim (read-line))]
    (log/info (format "Received: '%s'" line))
    (let [new-state
          (cond
            (.startsWith line "|INFO|start trick|") (start-trick client-state)
            (.startsWith line "|INFO|played|") (card-played client-state line)
            (.startsWith line "|INFO|end game|") nil
            (.startsWith line "|INFO|cards|") (receive-cards client-state line)
            (= line "|QUERY|card|END|") (send-card client-state)
            :else client-state)]
      (when (not (nil? new-state))
        (recur new-state)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -main
  [name & args]
  (run {:cards [] :played []}))
