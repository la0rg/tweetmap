var app = angular.module('tweetMapApp', ["leaflet-directive"]);

app.factory('Twitter', function($http, $timeout) {

    var ws = new WebSocket("ws://localhost:9000/ws");

    var twitterService = {
        tweets: [],
        query: function (query) {
            ws.send(JSON.stringify({query: query}));
        }
    };

    ws.onmessage = function(event) {
        $timeout(function() {
            twitterService.tweets = JSON.parse(event.data).statuses;
        });
    };

    return twitterService;
});

app.controller('Search', function($scope, $http, $timeout, Twitter) {

    $scope.search = function() {
        Twitter.query($scope.query);
    };

});

app.controller('Tweets', function($scope, $window, $http, $timeout, Twitter) {

    $scope.tweets = [];
    $scope.markers = [];

    $scope.$watch(
        function() {
            return Twitter.tweets;
        },
        function(tweets) {
            $scope.tweets = tweets;

            $scope.markers = tweets.map(function(tweet) {
                // note the three equal signs so that null won't be equal to undefined
                //if( "coordinates" in tweet ) {
                    return {
                        lng: tweet.coordinates[1],
                        lat: tweet.coordinates[0],
                        message: "<img src=" + tweet.user.profile_image_url + " />" + tweet.text,
                        focus: true
                   // }
                   // return null;
                }

            });
        }
    );
});