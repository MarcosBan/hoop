(ns webapp.connections.views.configuration-inputs
  (:require [clojure.string :as cs]
            [reagent.core :as r]
            [webapp.components.forms :as forms]))


(defn- config->inputs-labeled
  [{:keys [key value required placeholder]} index config]
  (let [key-val (r/atom key)
        value-val (r/atom value)
        save (fn [k v] (swap! config assoc-in [index k] v))]
    (fn []
      [:<>
       [forms/input {:classes "whitespace-pre overflow-x"
                     :on-change #(reset! value-val (-> % .-target .-value))
                     :on-blur #(save :value @value-val)
                     :label (cs/lower-case (cs/replace @key-val #"_" " "))
                     :required required
                     :placeholder (or placeholder key)
                     :type "password"
                     :value @value-val}]])))

(defn- config->inputs-files
  [{:keys [key value]} index config]
  (let [key-val (r/atom key)
        value-val (r/atom value)
        save (fn [k v] (swap! config assoc-in [index k] v))]
    (fn []
      [:<>
       [forms/input {:label "File name"
                     :id (str "file-name" @key-val)
                     :classes "whitespace-pre overflow-x"
                     :placeholder "kubeconfig"
                     :on-change #(reset! key-val (-> % .-target .-value))
                     :on-blur #(save :key @key-val)
                     :value @key-val}]
       [forms/textarea {:label "File content"
                        :id (str "file-content" @value-val)
                        :placeholder "Paste your file content here"
                        :on-change #(reset! value-val (-> % .-target .-value))
                        :on-blur #(save :value @value-val)
                        :value @value-val}]])))

(defn- config->inputs
  [{:keys [key value]} index config {:keys [is-disabled? is-required?]}]
  (let [key-val (r/atom key)
        value-val (r/atom value)
        save (fn [k v] (swap! config assoc-in [index k] v))]
    (fn []
      [:<>
       [forms/input {:label "Key"
                     :classes "whitespace-pre overflow-x"
                     :on-change #(reset! key-val (-> % .-target .-value))
                     :on-blur #(save :key @key-val)
                     :disabled is-disabled?
                     :required is-required?
                     :value @key-val
                     :placeholder "API_KEY"}]
       [forms/input {:label "Value"
                     :classes "whitespace-pre overflow-x"
                     :on-change #(reset! value-val (-> % .-target .-value))
                     :on-blur #(save :value @value-val)
                     :type "password"
                     :required is-required?
                     :value @value-val
                     :placeholder "* * * *"}]])))

(defn config-inputs-labeled
  [config attr]
  (doall
   (for [index (range (count @config))]
     ^{:key (str (get @config index) index)}
     [config->inputs-labeled (get @config index) index config attr])))

(defn config-inputs-files
  [config attr]
  (doall
   (for [index (range (count @config))]
     ^{:key (get @config index)}
     [config->inputs-files (get @config index) index config attr])))

(defn config-inputs
  [config attr]
  (doall
   (for [index (range (count @config))]
     ^{:key (get @config index)}
     [config->inputs (get @config index) index config attr])))
