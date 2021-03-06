## Validity functions and class definitions.
##------------------------------------------------------------------------------
## Validity functions
##------------------------------------------------------------------------------
## Pretty self-explainatory...
equalOrZero <- function(i, j) ((i==j)||(i==0))



## A helper function to check the columns of a data frame for their mode
checkMandatoryColumns <- function(object, name, mandatory, numeric=NULL, factor=NULL,
                                  character=NULL)
{
    obj <- slot(object, name)
    objcolnames <- varLabels(obj)
    missingColumns <- setdiff(mandatory, objcolnames)
    if(length(missingColumns)>0 | !length(objcolnames))
        return(sprintf("Column%s %s %s missing from slot %s",
                       ifelse(length(missingColumns)>1, "s ",""),
                       paste(missingColumns, collapse=", "),
                       ifelse(length(missingColumns)>1, " are"," is"),
                       name))

    for(j in intersect(numeric, objcolnames))
        if(!is.numeric(obj[[j]]) || any(is.na(obj[[j]])))
            return(sprintf("Column %s in '%s' must be numeric and not contain missing values.",
                           j, name))

    for(j in intersect(factor, objcolnames))
        if(!is.factor(obj[[j]]) || any(is.na(obj[[j]])))
            return(sprintf(paste("Column %s in '%s' must be a vector of factors and not",
                                 "contain missing values."), j, name))
    
    for(j in intersect(character, objcolnames))
        if(!is.character(obj[[j]]) || any(is.na(obj[[j]])))
            return(sprintf(paste("Column '%s' in '%s' must be a vector of characters",
                                 "and not contain missing values."), j, name))
}



## Check whether the object is up to date
isUpToDate <- function(object, error=FALSE)
{
    availSlots <- getObjectSlots(object)
    availSlotNames <- names(availSlots)
    definedSlotNames <- slotNames(object)
    valid <- setequal(availSlotNames, definedSlotNames)
    msg <- if(!valid) paste("This cellHTS object is out of date.\nPlease update using ",
                            "the 'updateCellHTS' function, e.g. updateCellHTS(",
                            substitute(object), ")", sep="") else NULL
    if(error && !valid) stop(msg, call.=FALSE)
    return(list(valid=valid, msg=msg))
}



## The main cellHTS object validity function
validityCellHTS <- function(object)
{
    if (!is(object, "cellHTS"))
        return(paste("cannot validate object of class", class(object)))
    ok <- isUpToDate(object)
    msg <- ok[["msg"]]
    if(!ok[["valid"]])
        return(msg)

    if(length(assayData(object))>0L)
    {
        msg <- checkMandatoryColumns(object, "phenoData", mandatory=c("replicate", "assay"),
                                     numeric=c("replicate"), character="assay")
        msg <- append(msg, checkMandatoryColumns(object, "featureData",
                                                 mandatory=c("plate", "well", "controlStatus"),
                                                 numeric=c("plate"), factor="controlStatus",
                                                 character="well"))
        ## add test to see whether column 'well' has the alphanumeric format (e.g. "A02")??
        ch <- assayDataElementNames(object)
    }
    if(!((length(object@state)==4L)&&
         (identical(names(object@state),
                    c("configured", "normalized", "scored", "annotated")))))
        msg <- append(msg, paste("'state' must be of length 4 and have names",
                                 "'configured', 'normalized', 'scored', 'annotated'"))
    if(is.null(msg))
        msg <- TRUE 
    return(msg)
}



## Main validy function for class "ROC"
validityROC <- function(object)
{
    if(!equalOrZero(length(object@TP), 1000L) ||
       !equalOrZero(length(object@FP), 1000L) ||
       (length(object@TP)!=length(object@FP)))
        return("'TP' and 'FP' should be vectors of integers with length 1000.")
    if(any(is.na(object@TP)))
        return("'TP' must not contain NA values.")
    if(any(is.na(object@FP)))
        return("'FP' must not contain NA values.")
    if(is.na(object@posNames))
        return("'posNames' must not contain NA values.")
    if(is.na(object@negNames))
      return("'negNames' must not contain NA values.")
    if(!(equalOrZero(length(object@assayType), 1)))
        return("'assayType' should be a character vector of length 1.")
    if(length(object@assayType)!=0)
    {
        if(!(object@assayType %in% c("two-way assay", "one-way assay")))
            return(paste("'assayType' should be one of the two options:",
                         "'one-way assay' or 'two-way assay'."))
    }
    return(TRUE)
}



##------------------------------------------------------------------------------
## Class cellHTS (inherits from Biobase class 'NChannelSet'
##------------------------------------------------------------------------------
setClass("cellHTS",  contains="NChannelSet",
 representation(plateList="data.frameOrNULL",
                intensityFiles="list",
                state="logical",
				processingInfo = "list",
                plateConf="data.frameOrNULL",
                screenLog="data.frameOrNULL",
                screenDesc="character",
                rowcol.effects="array",
                overall.effects="array",
                plateData="list"),
         prototype=prototype(new("VersionedBiobase",
         versions=c(classVersion("NChannelSet"), cellHTS="1.0.0")),
         plateList=data.frame(),
         intensityFiles=list(),
         state=c("configured"=FALSE, "normalized"=FALSE, "scored"=FALSE, 
         "annotated"=FALSE),
         plateConf=data.frame(),
         screenLog=data.frame(),
         screenDesc="",
         rowcol.effects=array(dim=c(0,0,0)),
         overall.effects=array(dim=c(0,0,0)),
         plateData=list(batch=data.frame())
    ),
  validity=validityCellHTS
) 



##------------------------------------------------------------------------------
## Class ROC
##------------------------------------------------------------------------------
setClass("ROC",
         representation(name="character",  
                        assayType="character",
                        TP="integer",
                        FP="integer",
                        posNames="character",
                        negNames="character"),
         prototype=list(
         name="",
         assayType=character(),
         TP=integer(),
         FP=integer(),
         posNames=character(),
         negNames=character()
         ),
         validity=validityROC)



## A class to capture modules of a cellHTS report. We keep this very simple yet generic:
##   title: the name/title of the module. This will later be used as caption on the tab in the final report
##   url: the url to the html code created for the module by htmlFun. The tab will later link to this url
##   htmlFun: an arbitrary function creating all the necessary HTML (and possibly also images). It has to accept three
##            mandatory argument 'cellHTSList', the list of raw, and -if available- normalized and scored cellHTS
##            objects, con, a file connection to write to, and the chtsModule object itself.
##            The return value of the function can be a list of additional
##            elements for the tabs data.frame which later serves as input to writeHtml.mainpage, i.e., everything
##            except 'url' and 'title', which is directly taken from the chtsModule object. If the return value is
##            NA, the respective tab will be omitted. This is useful to handle conditional generation of particular
##            modules in the htmFun function rather than directly in 'writeReport'.
##   funArgs: a list of values for additional function arguments. htmlFun will be called via 'do.call' and this list
## This should allow for simple extension of the report. In order to keep the code easier to read and understand,
## the computations and image generation in htmlFun should be kept separated from the rendering of HTML. Use the
## chtsImage class and its associated writeHtml method for all images to guarantee a similar look and feel.
setClass("chtsModule",
         representation=representation(title="character",
         url="character",
         htmlFun="function",
         funArgs="list"))

## constructor
chtsModule <- function(cellHTSList, title="anonymous", url=file.path(outdir, guid()), htmlFun=function(...){},
                       funArgs=list(cellHTSList=cellHTSList), outdir=".")
{
    if(! "cellHTSList" %in% names(funArgs))
        funArgs$cellHTSList <- cellHTSList
    new("chtsModule", url=url, htmlFun=htmlFun, funArgs=funArgs, title=title)
}





## A class to hold information about images on cellHTS reports. The writeHtml method of the class will produce
## the necessary HTML output, guaranteeing for a common look and feel. None of the slots except for the thumbnail
## are mandatory, and the HTML will be adapted to what is present. There is a notion of image stacks, and those can
## be supplied by the usual R vectorization (i.e. a vector of characters), in which case the HTML will provides the
## selection though tabs. Slots are:
##   shortTitle: a vector of characters used for the tabs to drill down into image stacks. This will be ignored if
##               only a single image is present
##   title: a character scalar or vector of titles for the images
##   caption: a character scalar or vector of subtitles
##   thumbnail: a character scalar or vector of urls to the bitmap versions of the image(s)
##   fullImage: a character scalar or vector of urls to the vectorized versions of the image(s)
##   additionalCode: a character scalar or vector of arbitrary HTML code to be added to the bottom of the image
##   map: a character scalar or vector of valid HTML imageMap code for each image
##   jsclass: a character scalar which is used to identify the image in the javascripts. Additional
##          classes for the respective channel and replicate versions of the image are augmented
##          automatically.
setClass("chtsImage",
         representation=representation(shortTitle="character",
         title="character",
         caption="character",
         thumbnail="character",
         fullImage="character",
         additionalCode="character",
         map="character",
         jsclass="character",
         tooltips="character"))

## constructor
chtsImage <- function(x)
{
    if(!is.data.frame(x))
        stop("'x' must be a data frame.")
    if(is.null(x$additionalCode))
        x$additionalCode <- ""
    if(is.null(x$map))
        x$map <- ""
    if(nrow(x)>1 && is.null(x$shortTitle))
        x$shortTitle <- paste("Image", seq_len(nrow(x)))
    new("chtsImage", thumbnail=as.character(x$thumbnail), fullImage=as.character(x$fullImage),
        shortTitle=as.character(x$shortTitle), title=as.character(x$title),
        additionalCode=as.character(x$additionalCode), map=as.character(x$map),
        caption=as.character(x$caption),
        jsclass=if(!is.null(x$jsclass)) as.character(x$jsclass) else "default")
}
                     




## A class to hold chtsImage objects for multiple channels and replicates
## Each element of the first list is supposed to be one channel, and the
## elements in the subsequent list are the replicates. For each channel and replicate
## there may be exactly one chtsImage object, possibly with multiple sub-images
setClass("chtsImageStack",
         representation(stack="list", id="character", title="character", tooltips="character"),
         prototype(stack=list(list())))

## constructor
chtsImageStack <- function(stack=list(list()), id, title=as.character(NULL), tooltips=as.character(NULL))
{
    nrChan <- length(stack)
    nrRep <- unique(sapply(stack, length))
    if(length(nrRep)!=1)
        stop("Need the same number of replicates or images for each channel")
    vals <- sapply(unlist(stack), is, "chtsImage")
    if(!all(vals))
        stop("All elements of the outer lists must be 'chtsImage' objects")
    if(length(tooltips) && length(tooltips) != length(stack[[1]]))
        stop("'tooltips' must be a vector of the same length as number of replicates")
    new("chtsImageStack", stack=stack, id=id, title=title, tooltips=tooltips)
}
