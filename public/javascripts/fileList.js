$(function() {
  var clip = new Clipboard('.copyBtn');
  $("button,input[type='button'],input[type='submit']").button();
  
  $(".removeBtn").button({
    icons: {
      primary: 'ui-icon-trash'
    },
    text: false
  });
  $(".removeBtn").click(function(e) {
    $("#fileId").attr("value", $(this).attr("data-id"));
    $("#removeForm").submit();
  });
});
