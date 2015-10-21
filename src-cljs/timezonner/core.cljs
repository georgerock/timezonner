(ns timezonner.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [ajax.core :refer [GET POST json-request-format json-response-format ajax-request]])
  (:import goog.History))

(defn nav-link [uri title page collapsed?]
  [:li {:class (when (= page (session/get :page)) "active")}
   [:a {:href uri
        :on-click #(reset! collapsed? true)}
    title]])

(defn navbar []
  (let [collapsed? (atom true)]
    (fn []
      [:nav.navbar.navbar-inverse.navbar-fixed-top
       [:div.container
        [:div.navbar-header
         [:button.navbar-toggle
          {:class         (when-not @collapsed? "collapsed")
           :data-toggle   "collapse"
           :aria-expanded @collapsed?
           :aria-controls "navbar"
           :on-click      #(swap! collapsed? not)}
          [:span.sr-only "Toggle Navigation"]
          [:span.icon-bar]
          [:span.icon-bar]
          [:span.icon-bar]]
         [:a.navbar-brand {:href "#/"} "TimeZonner"]]
        [:div.navbar-collapse.collapse
         (when-not @collapsed? {:class "in"})
         [:ul.nav.navbar-nav
          [nav-link "#/" "Home" :home collapsed?]
          [nav-link "#/about" "About" :about collapsed?]]
         [:ul.nav.navbar-nav.navbar-right
          [:li 
           (when (session/get :login-token)
             [:a {:href "/#/" 
                  :on-click #(session/put! :login-token nil)} 
              "Logout"])]]]]])))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12 [:h3 "TimeZonner is an application for Toptal"]]]])

(defn home-page []
  (if-let [token (session/get :id-token)]
    (secretary/dispatch! "/dashboard")
    (fn []
      [:div.container
       [:div.jumbotron
        [:h1 "Welcome to timezonner"]
        [:p "You can start adding your timezones"]
        [:p [:a.btn.btn-primary.btn-lg {:href "/#/login"} "Log in now »"] 
         " or " [:a {:href "/#/register"} "register"]]]])))

(defn zones-handler [[ok? response]]
  (if ok? 
    (do
     (session/put! :tzones (js->clj response))
     (session/put! :errors [])) 
    (session/put! :errors (conj (session/get :errors []) "Could not retrieve timezones"))))

(defn users-handler [[ok? response]]
  (if ok? 
    (do
     (session/put! :users (js->clj response))
     (session/put! :errors [])) 
    (session/put! :errors (conj (session/get :errors []) "Could not retrieve users"))))

(defn dash-page []
  (if (session/get :login-token) 
    (fn []
      (when (empty? (session/get :tzones []))
        (ajax-request {:uri "/api/timezones" 
                       :method :get
                       :headers {:content-type "application/json; charset=UTF-8" 
                                 :accept "application/json"
                                 :authorization (session/get :login-token)}
                       :response-format (json-response-format)
                       :handler #(zones-handler %)}))
      (when (empty? (session/get :users []))
        (ajax-request {:uri "/api/users" 
                       :method :get
                       :headers {:content-type "application/json; charset=UTF-8" 
                                 :accept "application/json"
                                 :authorization (session/get :login-token)}
                       :response-format (json-response-format)
                       :handler #(users-handler %)}))
      [:div.container
         [:div.row
          [:div.col-md-12 
           [:h3 "Timezones"]
           (for [error (session/get :errors)]
             [:div.alert.alert-danger {:role "alert"} error])
           [:form.form-inline
            [:div.form-group
             [:label "Name: "]
             [:input.form-control {:type :text}]]
            [:div.form-group
             [:label "City: "]
             [:input.form-control {:type :text}]]
            [:div.form-group
             [:label "Offset: "]
             [:input.form-control {:type :text}]]
            [:button.btn.btn-default "Add"]]
           [:table.table.table-striped.col-md-6               
            [:thead [:tr 
              [:th "Current time"]
              [:th "Name"]
              [:th "City"]
              [:th]]]
            [:tbody
             (for [tzone (session/get :tzones [])]
             [:tr {:key (str "timezone" (get tzone "id"))}                 
              [:td (str (-> (new js/Date) (.toGMTString)) " + (" (get tzone "offset") "h)")]
              [:td (get tzone "name")]
              [:td (get tzone "city")]
              [:td
               [:div.btn-group
                [:button.btn.btn-info "Update"]
                [:button.btn.btn-warning "Delete"]]]])]]]]
          [:div.row    
            [:div.col-md-12 
             [:h3 "Users"]
             [:form.form-inline
              [:div.form-group
               [:label "Name: "]
               [:input.form-control {:type :text}]]
              [:div.form-group
               [:label "Email: "]
               [:input.form-control {:type :email}]]
              [:div.form-group
               [:label "Password: "]
               [:input.form-control {:type :password}]]
              [:button.btn.btn-default "Add"]]
             [:table.table.table-striped.col-md-6               
              [:thead [:tr 
                [:th "Name"]
                [:th "Email"]
                [:th]]]
              [:tbody
               (for [user (session/get :users [])]
               [:tr {:key (str "user" (get user "id"))}                 
                [:td (get user "name")]
                [:td (get user "email")]
                [:td
                 [:div.btn-group
                  [:button.btn.btn-info "Update"]
                  [:button.btn.btn-warning "Delete"]]]])]]]]])
    (fn []
      [:div.container
         [:div.row
          [:div.col-md-12 
           [:p
            "You need to " 
            [:a {:href "/#/login"} "log in"]]]]])))

(defn row [label input]
  [:div.form-group
   [:label.col-sm-2.control-label label]
   [:div.col-sm-10 input]])

(defn input [label type id]
  (row label [:input.form-control 
              {:type type :id id
               :on-change #(let [login (session/get :login (hash-map))
                                 input-val (-> % .-target .-value)]
                                (session/put! :login (assoc login id input-val)))}]))

(defn login-handler [[ok? response]]
  (if ok? 
    (do 
     (session/put! :login-token (get (js->clj response) "token"))
     (session/put! :errors [])
     (set! (.-location js/window) "/#/dashboard")) 
    (session/put! :errors (conj (session/get :errors []) "Invalid login"))))

(defn login-check []
  (let [login (session/get :login)
        email (:email login)
        password (:password login)]
    (when (and email password)      
      (ajax-request {:uri "/api/login" 
                     :method :post
                     :body (.stringify js/JSON (clj->js login))
                     :headers {:content-type "application/json; charset=UTF-8" 
                               :accept "application/json"}
                     :response-format (json-response-format)
                     :handler #(login-handler %)}))))

(defn login-form []
  [:div.container
   [:div.row
    [:div.col-md-6.col-md-offset-3 
     [:h3 "Login"]
     (for [error (session/get :errors)]
       [:div.alert.alert-danger {:role "alert"} error]) 
     [:form.form-horizontal
       (input "Email" :email :email)
       (input "Password" :password :password)
       [:div.form-group
        [:div.col-sm-10.col-sm-offset-2 
         [:button.btn.btn-default {:on-click #(login-check)} "Login"]
         " or "
         [:a {:href "/#/register"} "create account"]]]]]]])

(defn login-page []  
  (if (session/get :login-token)
    (fn []
      [:div.container
         [:div.row
          [:div.col-md-12 
           [:p 
            "Login sucessful. Go to " 
            [:a {:href "/#/dashboard"} "the dashboard"]]]]])
    (login-form)))

(defn register-page []
  [:div.container
   [:div.row
    [:div.col-md-6.col-md-offset-3
     [:h3 "Register"]
     [:form.form-horizontal
      [:div.form-group
       [:label.col-sm-2.control-label "Name"]
       [:div.col-sm-10
        [:input.form-control {:type "text" :name "name"}]]]
      [:div.form-group
       [:label.col-sm-2.control-label "Email"]
       [:div.col-sm-10
        [:input.form-control {:type "email" :name "email"}]]]
      [:div.form-group
       [:label.col-sm-2.control-label "Password"]
       [:div.col-sm-10
        [:input.form-control {:type "password" :name "password"}]]]
      [:div.form-group
       [:div.col-sm-10.col-sm-offset-2
        [:input.btn.btn-default {:type "submit"
                                 :value "Register"}]
        " or "
        [:a {:href "/#/login"} "log in"]]]]]]])

(def pages
  {:home #'home-page
   :about #'about-page
   :login #'login-page
   :register #'register-page
   :dash #'dash-page})

(defn page []
  [(pages (session/get :page))])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :page :home))

(secretary/defroute "/about" []
  (session/put! :page :about))

(secretary/defroute "/login" []
  (session/put! :page :login))

(secretary/defroute "/register" []
  (session/put! :page :register))

(secretary/defroute "/dashboard" []
  (session/put! :page :dash))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (let [h (History.)]
   (goog.events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
   (doto h
    (.setEnabled true))))

(defn mount-components []
  (reagent/render [#'navbar] (.getElementById js/document "navbar"))
  (reagent/render [#'page] (.getElementById js/document "app")))

(defn init! []
  ;(fetch-docs!)
  (hook-browser-navigation!)
  (mount-components))
