# Initial setup

## install nodejs

    sudo apt-get install --yes nodejs

## install gulp

    npm install -g gulp

## install semantic-ui

    cd [laser]/app 
    npm install semantic-ui --save
    
The File semantic.json is automaticly build from your choises in the intallation prozess.
You have to choose the Folders for the source and the build
    
Result: app/nodes_moules 
    
## change CSS or JS in source ([laser]/app/semantic/src/..)

Our custom theme overrides some optional packaged themes, which override default theme.

 
##  overwrite (build) the files in destination: ([laser]/app/web-app/semantic/..)
 
    cd [laser]/app/semantic
    gulp build --> build all JS, CSS and other Resources
    
or
    
    gulp build-css -->like build but only css
    
or
    
    gulp watch -->Watch every change in folder [laser]/app/semantic and build 
    
-----------------------------------------------------


# How to customize CSS

## Theming

- we got the themes 'laser' and 'accessibility'
- all changes in css we make by changing inside the theme!
- the original semantic ui file for gulp bilding 'app/semantic/tasks/build.js' is changed in order to build two themes at the same time (laser & accessibility)
- meanwile the gulp build process temp files are builded and moved around

## Example

I would like to change the padding between an icon and content in a list

1.) find the variable in default theme. In this case there

    src/themes/default/elements/list.variables
    
2.) copy THE WHOLE list.variables and past it in the laser theme folder

    src/themes/laser/elements/list.variables
    
3.) make changes only there

4.) Change the theme.config 

    app/semantic/src/theme.config
    
old:

    @list       : 'default';
    
new:

    @list       : 'laser';
    
5.) Build css

    cd semantic
    gulp build

# Important Informations

## Datepicker

- 'by hand' implemented the sementic-ui datepicker
- it is not in current semantic-ui version (2.2)
- https://github.com/Semantic-Org/Semantic-UI/pull/3256/files
