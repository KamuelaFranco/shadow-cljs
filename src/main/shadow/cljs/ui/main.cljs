(ns shadow.cljs.ui.main
  (:require
    [fipp.edn :refer (pprint)]
    [shadow.experiments.arborist :as sa]
    [shadow.experiments.grove :as sg :refer (<< defc)]
    [shadow.experiments.grove.history :as history]
    [shadow.experiments.grove.worker-engine :as worker-eng]
    [shadow.lazy :as lazy]
    [shadow.cljs.model :as m]
    [shadow.cljs.ui.components.inspect :as inspect]
    [shadow.cljs.ui.components.build-status :as build-status]
    [shadow.cljs.ui.components.dashboard :as dashboard]
    [shadow.cljs.ui.components.runtimes :as runtimes]
    ))

(defc ui-builds-page []
  [{::m/keys [builds]}
   (sg/query-root
     [{::m/builds
       [::m/build-id]}])]
  (<< [:div.flex-1.overflow-auto
       [:div.p-2
        (sg/render-seq builds ::m/build-id
          (fn [{::m/keys [build-id] :as item}]
            (<< [:div.py-1
                 [:a.font-bold {:href (str "/build/" (name build-id))} (name build-id)]])))
        ]]))

(defc ui-build-page [build-ident]
  [data
   (sg/query-ident build-ident
     [:db/ident
      ::m/build-id
      ::m/build-target
      ::m/build-worker-active
      ::m/build-status])

   ::m/build-watch-compile! sg/tx
   ::m/build-watch-start! sg/tx
   ::m/build-watch-stop! sg/tx
   ::m/build-compile! sg/tx
   ::m/build-release! sg/tx]

  (let [{::m/keys [build-id build-target build-status build-worker-active]} data]
    (<< [:div.flex-1.overflow-auto
         [:h1.text-xl.px-2.py-4 (name build-id) " - " (name build-target)]
         [:div
          [:div.p-2.text-lg.font-bold "Actions"]
          [:div.p-2
           (if build-worker-active
             (<< [:button.py-2.px-4.bg-blue-200.hover:bg-blue-300.rounded.shadow
                  {:on-click [::m/build-watch-compile! build-id]}
                  "force compile"]
                 [:button.ml-2.py-2.px-4.bg-blue-200.hover:bg-blue-300.rounded.shadow
                  {:on-click [::m/build-watch-stop! build-id]}
                  "stop watch"])

             (<< [:button.py-2.px-4.bg-blue-200.hover:bg-blue-300.rounded.shadow
                  {:on-click [::m/build-watch-start! build-id]}
                  "start watch"]
                 [:button.ml-2.py-2.px-4.bg-blue-200.hover:bg-blue-300.rounded.shadow
                  {:on-click [::m/build-compile! build-id]}
                  "compile"]
                 [:button.ml-2.py-2.px-4.bg-blue-200.hover:bg-blue-300.rounded.shadow
                  {:on-click [::m/build-release! build-id]}
                  "release"]))]]

         [:div.p-2
          [:div.text-lg "Build Status"]
          (build-status/render-build-status build-status)]])))

(def ui-repl-page
  (sg/loadable-ui
    (lazy/loadable shadow.cljs.ui.components.eval/ui-repl-page)))

(defc ui-root* []
  [{::m/keys [current-page]}
   (sg/query-root
     [::m/current-page
      ;; load marker for suspense, ensures that all basic data is loaded
      ::m/init-complete])

   nav-items
   [{:pages #{:dashboard} :label "Dashboard" :path "/dashboard"}
    {:pages #{:builds :build} :label "Builds" :path "/builds"}
    {:pages #{:repl} :label "Runtimes" :path "/runtimes"}
    {:pages #{:inspect} :label "Inspect" :path "/inspect"}]

   nav-selected
   "inline-block rounded-t px-4 py-2 bg-blue-100 border-b-2 border-blue-200 hover:border-blue-400"
   nav-normal
   "inline-block px-4 py-2"]

  (<< [:div.flex.flex-col.h-full.bg-gray-100
       [:div.bg-white.shadow-md.z-10
        [:div.py-2.px-4
         [:span.font-bold "shadow-cljs"]]
        [:div
         (sg/render-seq nav-items :path
           (fn [{:keys [pages label path]}]
             (<< [:a
                  {:class (if (contains? pages (:id current-page)) nav-selected nav-normal)
                   :href path}
                  label])))]]

       (sg/suspense
         {:fallback "Loading ..."
          :timeout 500}
         (case (:id current-page)
           :inspect
           (inspect/ui-page)

           :builds
           (ui-builds-page)

           :build
           (ui-build-page (:ident current-page))

           :dashboard
           (dashboard/ui-page)

           :runtimes
           (runtimes/ui-page)

           :repl
           (ui-repl-page (:ident current-page))

           "Unknown Page"))]))

(defc ui-root []
  []
  (sg/suspense
    {:timeout 2000
     :fallback
     (<< [:div.inset-0.text-center.py-16
          [:div.text-2xl.font-bold "shadow-cljs"]
          [:div "Loading ..."]])}
    (ui-root*)))

(defonce root-el (js/document.getElementById "root"))

(defn ^:dev/after-load start []
  (sg/start ::ui root-el (ui-root)))

(defn init []
  (sg/init ::ui
    {}
    [(worker-eng/init js/SHADOW_WORKER)
     (history/init)])

  (js/setTimeout start 0))