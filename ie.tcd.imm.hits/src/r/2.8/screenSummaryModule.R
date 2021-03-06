## The workhorse function for the 'Screen Summary' module: an image plot of the results
## for the whole screen, possibly with an underlying HTML imageMap to allow for drill-down
## to the quality report page of the respective plates. Also a Normal Q-Q plot of the scores
## data.
writeHtml.screenSummary <- function(cellHTSList, module, imageScreenArgs, overallState,
                                  nrPlate, con, geneAnnotation)
{
    outdir <- dirname(module@url)
    if(overallState[["scored"]])
    {
        imgList <- list()
        ttInfo <- 
            if(overallState["annotated"]) "Table of scored <br/> and annotated probes" else
        "Table of scored probes"
        xsc <- cellHTSList$scored
        images <- list()
        imap <- list()
        for (ch in 1:dim(Data(xsc))[3])
        {
            name <- gsub("[^a-zA-Z0-9 ]", "_", paste("imageScreen", channelNames(cellHTSList$raw)[[ch]], sep="_"))
            res <- makePlot(outdir, con=con, name=name, w=7, h=7, psz=8,
                            fun=function(map=imageScreenArgs$map) do.call("imageScreen",
                                         args=append(list(object=xsc, map=map, channel=ch),
                                         imageScreenArgs[!names(imageScreenArgs) %in% "map"])),
                            print=FALSE, isImageScreen=TRUE)
            imgList[["Scores"]] <- chtsImage(data.frame(title="Screen-wide image plot of the scored values",
                                                        thumbnail=paste(name,"png", sep="."), 
                                                        fullImage=paste(name, "pdf", sep="."),
                                                        map=
                                                                if(!is.null(res))
                                                                    screenImageMap(object=res$obj,
                                                                            tags=res$tag, paste(name, "png", sep="."),
                                                                            cellHTSlist=cellHTSList, imageScreenArgs=imageScreenArgs, channel=ch,
                                                                            geneAnnotation=geneAnnotation)
                                                                else
                                                                    NA))

            qqName <- gsub("[^a-zA-Z0-9 ]", "_", paste("qqplot", channelNames(cellHTSList$raw)[[ch]], sep="_"))
            qqn <- makePlot(outdir, con=con, name=qqName, w=7, h=7, psz=8,
                    fun=function(x=xsc)
                        {
                            par(mai=c(0.8,0.8,0.2,0.2))
                            qqnorm(Data(x)[,,ch, drop=FALSE], main=NULL, cex.lab=1.3)
                            qqline(Data(x)[,,ch, drop=FALSE], col="darkgray", lty=3)
                        },
                        print=FALSE)
            imgList[["Q-Q Plot"]] <- chtsImage(data.frame(title="Normal Q-Q Plot",
                                                          thumbnail=paste(qqName, "png", sep="."),
                                                          fullImage=paste(qqName, "pdf", sep=".")
                                                          ))
            images <- append(images, list(imgList))
        }
        names(images) <- channelNames(cellHTSList$raw)
        stack <- chtsImageStack(images, id="imageScreen",
                                tooltips=addTooltip(names(imgList), "Help"))        
        writeHtml.header(con)
        writeHtml(stack, con)
        writeHtml.trailer(con)
        return(NULL)
    }
    else
    {
        return(NA)
    }
}



## This function is used to split the Screen-wide image plot of the scored values into rectangle
## areas for a HTML imageMap in order that clicking on a plate will link to its quality report.
screenImageMap <- function(object, tags, imgname, cellHTSlist=cellHTSlist,
                           imageScreenArgs=imageScreenArgs, channel, geneAnnotation)
{			
    ## imageScreen configuration, same as in file imagescreen.R
    xsc <- cellHTSlist$scored	
    nr <- pdim(xsc)[1] ## number of rows for the plate
    nc <- pdim(xsc)[2] ## number of columns for the plate
    ## 'ar' is the aspect ratio for the image plot
    ##(i.e. number columns divided by the number of rows)
    ar <- imageScreenArgs$ar
    nrPlates <- getNrPlateColRow(ar, xsc)$nrPlates ## number of plates
    nrRow <- getNrPlateColRow(ar, xsc)$nrRow ## number of plates per row in imageScreen.png
    nrCol <- getNrPlateColRow(ar, xsc)$nrCol ## number of plates per column in imageScreen.png
	
    ## beginning of the html code
    mapname <- paste("map", gsub(" |/|#", "_", imgname), sep="_") 	
    outin <- sprintf("usemap=\"#%s\"><map name=\"%s\" id=\"%s\">\n", mapname, mapname, mapname)
	
    ## links to the plate report
    plateCounter <- 1
    remainingPlates <- nrPlates
    out <- ""
    for(i in (1:nrRow))
    {
        ## initialization; useful for the last row which may contain less than nrCol plates
        tempCol <-  if(remainingPlates<nrCol) remainingPlates else nrCol
        
        ## coords
        xi <- object[(0:(tempCol-1))*nc+1,1]
        xf <- object[(0:(tempCol-1))*nc+(nc-1),3]		
        yi <- rep(object[1+(i-1)*nc*nrCol*nr,2], tempCol)
        yf <- rep(object[1+(i-1)*nc*nrCol*nr+nc*tempCol*(nr-1),4], tempCol)
        for(j in 1:tempCol)
        {
            dat <- as.vector(Data(xsc)[,1,channel])
            for (x in 1:pdim(xsc)[2])
            {
                for (y in 1:pdim(xsc)[1])
                {
                    toAdd <- matrix(c(round(xi + (x-1) * (xf-xi) / (pdim(xsc)[2] - 1),0),round(yi + (y-1) * (yf-yi) / pdim(xsc)[1],0),round(xi + x * (xf-xi) / (pdim(xsc)[2] - 1),0),round(yi+y * (yf-yi) / pdim(xsc)[1],0)),ncol=4)
                    newLine <- paste(paste("<area shape=\"rect\" coords=\"",
                                           paste(toAdd[j,], collapse=","),"\"", sep=""),
                                     paste(" ", paste(names(tags), "=\"",
                                                      c(paste(geneAnnotation[x+ (y-1 + (plateCounter - 1) * pdim(xsc)[1]) * pdim(xsc)[2]], '(plate:', plateCounter, "well:", paste(LETTERS[y], x, sep=""), ") score:", round(dat[x + (y-1 + (plateCounter - 1) * pdim(xsc)[1]) * pdim(xsc)[2]],2)
                              ,sep=' '),
                                                        paste("..", plateCounter, 'index.html', sep='/')),
                                                      "\"", sep=""), collapse=" "), " alt=\"\"/>\n",
                                    sep="")
                    out <- paste(out, newLine)	
                }
            }
            plateCounter <- plateCounter+1
        }			
        remainingPlates <- remainingPlates-tempCol			
    }
    out <- paste(outin, out, "</map",sep="")
    return(out)
} 



