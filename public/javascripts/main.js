/**
 * Created by lmarini on 1/21/15. MOdified MO 3/16/16
 */
function notify(text, type, logConsole, timeout) {
    // default parameters
    if (typeof(text)==='undefined') text = "Notification improperly created";
    if (typeof(type)==='undefined') type = "alert";
    if (typeof(logConsole)==='undefined') logConsole = false;
    if (typeof(timeout)==='undefined') timeout = false;

    var txt = '<div>' + text + '<span id="notyCloseButton" class="close-notify">x</span></div>';
    noty({
        layout: 'topCenter',
        theme: 'relax',
        type: type,
        text: txt,
        timeout: timeout,
        closeWith: ['click'],
        //Needed? I get an error about noty.close not being a function, and things appear to work without the callback...
        callback : {
            afterShow: function() {
                $('#notyCloseButton').one('click', function() {
                    noty.close();
                });
            }
        }
    });

    if (logConsole) console.log(type + ": " + text);
}

window['notify'] = notify;