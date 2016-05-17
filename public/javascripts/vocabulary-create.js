//Clear the input fields that are associated with this panel
function clearFields() {
	$('#name').val("");
    $('#keys').val("");
	$('#description').val("");
}

//Remove the error messages that are provided to the user
function clearErrors() {
	$('.error').hide();
}

//Call on Create button click. Move to create a collection as specified after validating input fields
function createVocabulary() {
	//Remove error messages if present
	clearErrors();

	//Update the input we are adding to the form programmatically
	var name = $('#name');
    var keys = $('#keys');
    console.log(keys)
    var desc = $('#description');
    var space = $('#spaceid').find(":selected").val();
    var spaceList = [];
    $('#spaceid').find(":selected").each(function(i, selected) {
        spaceList[i] = $(selected).val()
    });

    //Add errors and return false if validation fails. Validation comes from the host page, passing in the isNameRequired and isDescRequired
    //variables.
    var error = false;
    if (!name.val() && isNameRequired) {
    	$('#nameerror').show();
    	error = true;
    }
    if (!keys.val() && isKeysRequired) {
        $('#keyerror').show();
        error = true;
    }
    if (!desc.val() && isDescRequired) {
        $('#descerror').show();
        error = true;
    }
    if (error) {
    	return false;
    }

    var encName = htmlEncode(name.val());
	var encDescription = htmlEncode(desc.val());
    var encKeys = htmlEncode(keys.val());
	$('#hiddenname').val(encName);
    $('#hiddenkeys').val(encKeys)
    $('#hiddendescription').val(encDescription);
    $('#hiddenspace').val(spaceList);

	//Submit the form
    $('#collectioncreate').submit();

    return true;
}
