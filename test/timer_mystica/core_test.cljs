(ns ^:figwheel-always timer-mystica.core-test
  (:require
    [cljs.test :refer-macros [deftest testing is]]
    [figwheel.client :as fw]
    [timer-mystica.core :as tm]
    [timer-mystica.test-formatter]))

; Test data

(def witches {:faction :witches :time-used-ms 111111})
(def dwarves {:faction :dwarves :time-used-ms 222222})
(def nomads {:faction :nomads :time-used-ms 333333})
(def darklings {:faction :darklings :time-used-ms 444444})
(def ice-maidens {:faction :ice-maidens :time-used-ms 55555})

(def base-game-state {:current-player  witches
                      :active-players  []
                      :passed-players  []
                      :round           2
                      :between-rounds? false})

(def base-meta-state {:paused        false
                      :history       []
                      :history-index 0
                      :game-state    base-game-state})

(defn game-state [& kvs]
  (merge base-game-state (apply hash-map kvs)))

(defn meta-state [& kvs]
  (merge base-meta-state (apply hash-map kvs)))

; Tests

(deftest test-player-selected-next
  (testing "Simple case"
    (let [initial-state (game-state :current-player witches
                                    :active-players [dwarves nomads darklings])
          updated-state (tm/player-selected-next initial-state)
          expected-state (game-state :current-player dwarves
                                     :active-players [nomads darklings witches])]
      (is (= updated-state expected-state))))
  (testing "Last player in round"
    (let [initial-state (game-state :current-player witches
                                    :active-players []
                                    :passed-players [dwarves nomads darklings])
          updated-state (tm/player-selected-next initial-state)]
      (is (= updated-state initial-state)))))

(deftest test-player-selected-pass
  (testing "Simple case"
    (let [initial-state (game-state :current-player witches
                                    :active-players [dwarves nomads]
                                    :passed-players [darklings ice-maidens])
          updated-state (tm/player-selected-pass initial-state)
          expected-state (game-state :current-player dwarves
                                     :active-players [nomads]
                                     :passed-players [darklings ice-maidens witches])]
      (is (= updated-state expected-state))))
  (testing "Last player passes"
    (let [initial-state (game-state :current-player witches
                                    :active-players []
                                    :passed-players [dwarves nomads darklings]
                                    :round 2
                                    :between-rounds? false)
          updated-state (tm/player-selected-pass initial-state)
          expected-state (game-state :current-player dwarves
                                     :active-players [nomads darklings witches]
                                     :passed-players []
                                     :round 3
                                     :between-rounds? true)]
      (is (= updated-state expected-state)))))

(deftest test-advance-time
  (testing "Add to current player when in play"
    (let [initial-state (meta-state :paused? false
                                    :game-state
                                    (game-state :current-player {:faction      :witches
                                                                 :time-used-ms 300}
                                                :between-rounds?) false)
          updated-state (tm/advance-time initial-state 200)
          expected-state (assoc-in initial-state [:game-state :current-player :time-used-ms] 500)]
      (is (= updated-state expected-state))))
  (testing "Don't add to time when between rounds"
    (let [initial-state (meta-state :paused? false
                                    :game-state
                                    (game-state :current-player {:faction      :witches
                                                                 :time-used-ms 300}
                                                :between-rounds? true))
          updated-state (tm/advance-time initial-state 200)]
      (is (= updated-state initial-state))))
  (testing "Don't add to time when paused"
    (let [initial-state (meta-state :paused? true
                                    :game-state
                                    (game-state :current-player {:faction      :witches
                                                                 :time-used-ms 300}
                                                :between-rounds?) false)
          updated-state (tm/advance-time initial-state 300)]
      (is (= updated-state initial-state)))))

(deftest test-start-round
  (let [initial-state (game-state :between-rounds? true)
        updated-state (tm/start-round initial-state)
        expected-state (game-state :between-rounds? false)]
    (is (= updated-state expected-state))))

(deftest test-update-game-state-add-history
  (testing "It should populate an empty history"
    (let [initial-state (meta-state :history []
                                    :history-index 0
                                    :game-state 10)
          updated-state (tm/update-game-state-add-history initial-state inc)
          expected-state (meta-state :history [10]
                                     :history-index 1
                                     :game-state 11)]
      (is (= updated-state expected-state))))
  (testing "It should append updated state to history and update index"
    (let [initial-state (meta-state :history [:a :b]
                                    :history-index 2
                                    :game-state 10)
          updated-state (tm/update-game-state-add-history initial-state inc)
          expected-state (meta-state :history [:a :b 10]
                                     :history-index 3
                                     :game-state 11)]
      (is (= updated-state expected-state))))
  (testing "It should clobber history after the current index"
    (let [initial-state (meta-state :history [:a :b :c :d]
                                    :history-index 2
                                    :game-state 10)
          updated-state (tm/update-game-state-add-history initial-state inc)
          expected-state (meta-state :history [:a :b 10]
                                     :history-index 3
                                     :game-state 11)]
      (is (= updated-state expected-state))))
  (testing "It should use current game-state as first argument to update function"
    (let [initial-state (meta-state :history [:a :b]
                                    :history-index 2
                                    :game-state 10)
          updated-state (tm/update-game-state-add-history initial-state - 3)
          expected-state (meta-state :history [:a :b 10]
                                     :history-index 3
                                     :game-state 7)]
      (is (= updated-state expected-state)))))

(deftest test-undo
  (testing "If in history, should revert to previous index and discard current state"
    (let [initial-state (meta-state :history [:a :b :c :d]
                                    :history-index 2
                                    :game-state :x)
          updated-state (tm/undo initial-state)
          expected-state (meta-state :history [:a :b :c :d]
                                     :history-index 1
                                     :game-state :b)]
      (is (= updated-state expected-state))))
  (testing "If past end of history, should revert to last state and append current to history"
    (let [initial-state (meta-state :history [:a :b]
                                    :history-index 2
                                    :game-state :x)
          updated-state (tm/undo initial-state)
          expected-state (meta-state :history [:a :b :x]
                                     :history-index 1
                                     :game-state :b)]
      (is (= updated-state expected-state)))))

(deftest test-redo
  (testing "Should advance to next index and discard current state"
    (let [initial-state (meta-state :history [:a :b :c :d]
                                    :history-index 1
                                    :game-state :x)
          updated-state (tm/redo initial-state)
          expected-state (meta-state :history [:a :b :c :d]
                                     :history-index 2
                                     :game-state :c)]
      (is (= updated-state expected-state))))
  (testing "Redo to end of history should clear last history item"
    (let [initial-state (meta-state :history [:a :b :c :d]
                                    :history-index 2
                                    :game-state :x)
          updated-state (tm/redo initial-state)
          expected-state (meta-state :history [:a :b :c]
                                     :history-index 3
                                     :game-state :d)]
      (is (= updated-state expected-state)))))

;; Test runner

(defn run-tests []
  (.clear js/console)
  (cljs.test/run-all-tests #"timer-mystica.*-test"))

(run-tests)

;; FW connection is optional in order to simply run tests,
;; but is needed to connect to the FW repl and to allow
;; auto-reloading on file-save
(fw/start {:websocket-url "ws://localhost:3449/figwheel-ws"
           :build-id      "test"})
