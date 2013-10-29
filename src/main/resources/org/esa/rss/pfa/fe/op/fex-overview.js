function fex_openCsv(doc) {
    var featureTable = doc.getElementById('fTable');
    var numRows = featureTable.rows.length - 1; // subtract header row

    //alert("Type: " + doc.toString())
    mywin = window.open('about:blank', "_blank");

    mywin.document.writeln("<html>");
    mywin.document.writeln("<head>");
    mywin.document.writeln("<title>Result from " + doc.title.value + "</title>");
    mywin.document.writeln("</head>");
    mywin.document.writeln("<body>");
    mywin.document.writeln("<table>");
    for (var i = 0; i < numRows; i++) {
        var id = "label" + i;
        var label = doc.getElementById(id);
        mywin.document.writeln("<tr>");
        mywin.document.writeln("<td>" + i + "</td>");
        mywin.document.writeln("<td>" + label.name + "</td>");
        mywin.document.writeln("<td>" + label.value + "</td>");
        mywin.document.writeln("</tr>");
    }
    mywin.document.writeln("</table>");
    mywin.document.writeln("</body>");
    mywin.document.writeln("</html>");
    mywin.document.close();

    mywin.focus();
}
