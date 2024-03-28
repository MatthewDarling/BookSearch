(ns app.ui
  (:require
   [app.semantic-ui :as sui] 
   [clojure.string]
   [uix.core :as uix :refer [defui $]]
   [uix.dom]))

;; Header ---------------------------------------------------------------------
(defui header 
  []
  ($ :header.app-header
     ($ :h1 "Book Search")
     ($ :h2 "Demonstration of Full-Text Search on Large Texts")
     ($ :.main-icon ($ sui/book-icon {:class [:large :blue]}))))

;; Search Bar -----------------------------------------------------------------
(defui text-field
  [{:keys [on-search initial-value has-error loading]}]
  (let [[value set-value!] (uix/use-state initial-value)] 
    ($ :<>
       ($ sui/input
          {:value       value
           :disabled    (boolean loading)
           :type        :text
           :class       [(when loading :loading)
                         (when has-error :error)
                         :big
                         :text-input]
           :placeholder "Enter a search term"
           :on-change   (fn [^js e]
                          (set-value! (.. e -target -value)))
           :on-key-down (fn [^js e]
                          (when (= "Enter" (.-key e))
                            (on-search value)))})
       ($ :button.text-input-button
          {:on-click (fn [^js e]
                       (on-search value))
           :data-tooltip "Search across the database of book content"
           :data-position "bottom right"}
          ($ :i.circular.inverted.link.icon.blue
             {:class (if loading
                       [:loading :spinner]
                       [:search])})))))

(defui loading-bar
  "Loading bar for UI which estimates the completion of a process as being
   (time_factor X 100) milliseconds. Displays the loading bar and counts 
   the actual time while playing as animated loading bar."
  [{:keys [time-factor]}]
  (let [[load-time _] (uix/use-state (js/Date.now))
        [interval set-interval!] (uix/use-state nil)
        bar-elem (uix/use-ref)
        counter-elem (uix/use-ref)]
    (uix/use-effect
     (fn [_] 
       (when (and @counter-elem @bar-elem (not interval)) 
         (-> #(when @bar-elem
                (let [current-time (- (js/Date.) load-time)]
                  (set! (.-width (.-style @bar-elem))
                        (str
                         (js/Math.min
                          (/ (- (js/Date.) load-time)
                             time-factor)
                          100)
                         "%"))
                  (set! (.-innerHTML @counter-elem)
                        (str current-time "ms"))))
             (js/setInterval 67)
             set-interval!))
       (fn [_]
         (js/clearInterval interval)))
     [load-time interval time-factor])
    ($ sui/file-loader
       ($ sui/blue-bar
          {:ref bar-elem
           :style {:transition-duration "100ms"}})
       ($ sui/label "Performing Search " 
          ($ :span#loading-counter {:ref counter-elem})))))


(defui query-stats 
  "Displays the total time to query "
  [{:keys [time-start time-end]}]
  ($ :div.query-stats
   ($ :div
     {:class [:ui :label :right :ribbon :label :very :large
              (cond (<= (- time-end time-start) 0)
                    :secondary
                    (< (- time-end time-start) 1000)
                    :green
                    (< (- time-end time-start) 2500)
                    :orange
                    :else
                    :red)]}
     "Query results returned in " 
     ($ :.detail (if (> (- time-end time-start) 0)
                       ($ :<> (- time-end time-start) "ms")
                       "-")))))

(defui file-header
  "Ui Component to convert a plain text (large) blob of text into HTML"
  [{:files/keys [file_id title author]}]
  ($ :header
     {:key (keyword file_id) :class "file-title"}
     ($ :h3 title)
     ($ :h4 author)))

(defui file-placeholder 
  "Draws a placeholder representation of a document or file"
  [{:keys [key]}]
  ($ sui/placeholder
     {:key key}
     ($ :.line)
     ($ :.line)
     ($ :.line)
     ($ :.header.image
        ($ :.line)
        ($ :.line))
     ($ :.paragraph
        ($ :.medium.line)
        ($ :.short.line))))

(defui file-error 
  "Dialog to display error when searching file" 
  [error]
  ($ :dialog.file-error {:open true}
     ($ :.ui.red.ribbon.label "Error")
     error))

(defui file-search-results 
  [{:keys [search-results result-no class-list]}]
  ($ sui/content
     ($ :i.icon.bullseye.green)
     (inc result-no)
     " of "
     (.-length class-list)
     " results matching:"
     ($ :.sub.header
        ($ :ul.file-search-results-list
           (mapv 
            #($ :li {:key (:term %)}
                     ($ sui/h4 (:term %)))  
            search-results)))))

;; Radio Options As Buttons ---------------------------------------------------

(defui strategy-radio
  [{:keys [strategy value on-set-strategy tooltip]}]
  ($ sui/button
     {:class [(when (= strategy value) :blue)
              :strategy-buttons]
      :on-click (fn [_] (on-set-strategy value))
      :value value
      :data-tooltip tooltip
      :data-position "bottom left"}
     (if (= strategy value)
       ($ sui/tach-icon)
       ($ sui/circle-icon))
     value))

(defui strategy-radio-options
  [{:keys [strategy on-set-strategy class]}]
  (let [strategy (cond-> strategy (keyword? strategy) name)]
    ($ sui/form
       ($ sui/buttons
          {:class [class]}
          ($ strategy-radio
             {:value "ts_fast_headline"
              :on-set-strategy on-set-strategy
              :strategy strategy
              :tooltip "Uses pre-realized search and recall lookup columns,  
                        performing 5x-20x faster than ts_headline."})
          ($ :.or)
          ($ strategy-radio
             {:value "ts_headline"
              :on-set-strategy on-set-strategy
              :strategy strategy
              :tooltip "PostgreSQL's built-in content retrieval and 
                        highlighing function."})
          ($ :.or)
          ($ strategy-radio
             {:value "ts_semantic_headline"
              :on-set-strategy on-set-strategy
              :strategy strategy
              :tooltip "Extends ts_headline with TS phrase and boolean semantics; 
                        requiring 5-10% more time versus ts_headline"})))))

(defui mode-radio
  [{:keys [mode value on-set-mode tooltip loading]}]
  ($ sui/button
     {:class (when (= mode value) :teal)
      :on-click (fn [_] (on-set-mode value))
      :value value
      :checked (= mode value)
      :disabled (boolean loading)
      :data-tooltip tooltip
      :data-position "bottom right"}
     ($ (cond loading
              :i.inverted.spinner.link.icon.blue
              (= mode value)
              sui/play-icon
              :else
              sui/circle-icon)
        {:class [(when loading :loading)
                 :ui
                 :link
                 :search
                 :icon]})
     value))

(defui query-mode-options
  [{:keys [mode on-set-mode class loading]}]
  ($ sui/form
     {:class :mode-controls}
     ($ sui/buttons
        {:class [class :fluid]}
        ($ mode-radio
           {:value "phrase"
            :on-set-mode on-set-mode
            :mode mode
            :loading loading
            :tooltip "Evaluates search term as a multi-word phrase"})
        ($ :.or)
        ($ mode-radio
           {:value "logical"
            :on-set-mode on-set-mode
            :mode mode
            :loading loading
            :tooltip "Evaluates search term as a boolean expression. 
                      Use | for OR, & for AND, ! for NOT and <n> of WORD DISTANCE."}))))
