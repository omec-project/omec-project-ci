# SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: Apache-2.0
# Generates trend graph for ng40 functionality tests

print( "**********************************************************" )
print( "STEP 1: Data management." )
print( "**********************************************************" )

# Command line arguments are read. Args include the database credentials, test name, branch name, and the directory to output files.
print( "Reading commmand-line args." )
args <- commandArgs(trailingOnly=TRUE)

if (length(args) < 3){
    print("Usage: Rscript trend.R <config-file> <data-file-directory> <output-path-filename>")
    q(status=1)
}

print("Importing libraries.")
library(ggplot2)
library(ggrepel)
library(reshape2)
library(readr)
library(rjson)

config <- fromJSON(file = args[1])
config

outputFile <- args[3]

# Get list of files from data directory
fileList <- list.files(path=args[2], pattern="*.csv")
data <-
  do.call("rbind",
          lapply(fileList,
                 function(x)
                 read.csv(paste(args[2], x, sep=''),
                 stringsAsFactors = FALSE)))

# Use only latest x data determined from config file.
usableData <- tail(data, config$builds_to_show)

# **********************************************************
# STEP 2: Organize data.
# **********************************************************

print( "**********************************************************" )
print( "STEP 2: Organize Data." )
print( "**********************************************************" )

# Exclude "build" column in dataframe
dataFrame <- melt(usableData[c("planned_cases", "passed_cases", "failed_cases")])

# Rename column names in dataFrame
colnames(dataFrame) <- c("Status",
                         "Quantity")

dataFrame$failed_cases <- usableData$failed_cases
dataFrame$passed_cases <- usableData$passed_cases
dataFrame$planned_cases <- usableData$planned_cases
dataFrame$build <- usableData$build

# Adding a temporary iterative list to the dataFrame so that there are no gaps in-between build numbers.

dataFrame$iterative <- seq(0, nrow(usableData) - 1, by=1)
print(dataFrame)
# **********************************************************
# STEP 3: Generate graphs.
# **********************************************************

print( "**********************************************************" )
print( "STEP 3: Generate Plot." )
print( "**********************************************************" )

# -------------------
# Main Plot Formatted
# -------------------

print( "Formatting main plot." )

mainPlot <- ggplot(data=dataFrame, aes(x=iterative,
                                       y=Quantity,
                                       color=Status))

# set the default text size of the graph.
theme_set( theme_grey( base_size = 26 ) )

# geom_ribbon is used so that there is a colored fill below the lines. These values shouldn't be changed.

facColor <- geom_ribbon( aes( ymin = 0,
                             ymax = failed_cases ),
                             fill = "#FF0000",
                             linetype = 0,
                             alpha = 0.07 )

pacColor <- geom_ribbon( aes( ymin = 0,
                             ymax = passed_cases ),
                             fill = "#33CC33",
                             linetype = 0,
                             alpha = 0.07 )

plcColor <- geom_ribbon( aes( ymin = 0,
                             ymax = planned_cases ),
                             fill = "#3399FF",
                             linetype = 0,
                             alpha = 0.02 )

# X-axis config
xScaleConfig <- scale_x_continuous( breaks = dataFrame$iterative,
                                   label = dataFrame$build )
# Y-axis config
yScaleConfig <- scale_y_continuous( breaks = seq( 0, max( dataFrame$planned_cases ),
                                   by = ceiling( max( dataFrame$planned_cases ) / 10 ) ) )

# Axis labels
xLabel <- xlab(config$x_axis_title)
yLabel <- ylab(config$y_axis_title)

# Title of plot
title <- labs( title = config$graph_title, subtitle = paste( "Last Updated: ", format( Sys.time(), "%b %d, %Y at %I:%M %p %Z" ), sep="" ) )

# Other theme options
theme <- theme( plot.title = element_text( hjust = 0.5, size = 32, face ='bold' ),
           axis.text.x = element_text( angle = 0, size = 14 ),
           legend.position = "bottom",
           legend.text = element_text( size = 22 ),
           legend.title = element_blank(),
           legend.key.size = unit( 1.5, 'lines' ),
           legend.direction = 'horizontal',
           plot.subtitle = element_text( size=16, hjust=1.0 ) )

# Wrap legend
wrapLegend <- guides( color = guide_legend( nrow = 1, byrow = TRUE ) )

# Colors for the lines
lineColors <- scale_color_manual( labels = c( "Planned Cases",
                                              "Passed Cases",
                                              "Failed Cases"),
                                  values=c( "#3399FF",
                                           "#33CC33",
                                           "#FF0000") )

# Store plot configurations as 1 variable
fundamentalGraphData <- mainPlot +
                        pacColor +
                        plcColor +
                        facColor +
                        xScaleConfig +
                        yScaleConfig +
                        xLabel +
                        yLabel +
                        theme +
                        wrapLegend +
                        lineColors +
                        title

print( "Generating line plot." )

lineGraphFormat <- geom_line( size = 1.1 )
pointFormat <- geom_point( size = 4 )
pointLabel <- geom_text_repel(aes(label=Quantity),
                              size = 6)

result <- fundamentalGraphData +
           lineGraphFormat +
           pointFormat +
           pointLabel

imageWidth <- 21
imageHeight <- 9
imageDPI <- 200

print("Saving plot...")

ggsave( outputFile,
         width = imageWidth,
         height = imageHeight,
         dpi = imageDPI )

print("Success")
