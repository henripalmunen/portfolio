
An original project for the course Programming Studio 2 at Aalto University
Author: Henri Palmunen

The Data dashboard is an interactive data visualization program that can be used to visualize local data stored in Excel spreadsheets or financial
data fetched from Yahoo Finance. Charts can be edited, duplicated and deleted, but they can’t be moved or resized in a way that makes them overlap
with each other or the main window’s borders. A clicked datapoint’s info can be seen on the bottom right of the app. The bottom left displays a
clicked chart’s basic stats. A dashboard session can be saved as a .json file, and a user can continue from where they left off later by opening
said save file.

NOTE: the program's core functionalities are well in place, but there are plenty of rough edges to smooth out were I to continue working on it.

The save files only contain the address (filepath or URL) where a chart can find its data, not the data itself. This means a save file is corrupted
if the files it uses are moved.


Usage

To use the program, run object DashboardApp in folder src/main/scala/ui.

You can create charts via the “Create chart” menu. The menu has two sections - “Local” and “Financial” - for the two different data fields. The program
is able to display .xlsx-files and financial info on any internationally listed company.

A spreadsheet should have its data stored in adjacent columns. The first non-empty column stores X values (strings or numbers), and columns to its right store
all the different series of data.

If the user wants to visualize numeric data (such as measurements in a physics lab), they should use line charts or bar charts. Categorical data
can be visualized with pie charts. X values must be numeric for line - and bar charts and strings for pie charts. All Y values must be numeric.

If the user is interested in the latest trends in finance, they can create a financial chart by providing a financial asset’s ticker and a
timeframe. Financial charts can be found in the “Financial” menu under “Create chart”.

When a chart is created, it can be moved around or resized. The user can drag the small square icons in a chart’s corners to resize it. It can also
be edited or duplicated by double clicking.

Multiple charts can be selected by dragging over them. A rectangular selector will appear, and the selected charts will be highlighted in gray.

Selected charts can be deleted using backspace. If the underlying data of a chart has changed, these changes can be loaded by pressing R. This will
update the data for all charts in the window.

The main window of the app is resizable, but this isn’t recommended because no functionality for zooming has been implemented. This makes charts
crop partially or completely out of the window if the window is resized too small.

If the user wishes to save their dashboard session, they can click the “Save” icon under the “File” menu. They can assign a destination and folder
for the .json file and then continue with their session. If they wish to load a previous session, they can click the “Open” icon in the same menu
and find a previously saved .json file.
